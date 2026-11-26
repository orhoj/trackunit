package com.orhoj.trackunit.demo.mapper;

import com.orhoj.demo.hiremanager.model.Account;
import com.orhoj.demo.hiremanager.model.Address;
import com.orhoj.demo.hiremanager.model.Hire;
import com.orhoj.demo.hiremanager.model.HireLine;
import com.orhoj.demo.hiremanager.model.Machine;
import com.orhoj.demo.rentalapi.model.AddressRepresentation;
import com.orhoj.demo.rentalapi.model.RentalRateRepresentation;
import com.orhoj.demo.rentalapi.model.SyncAsset;
import com.orhoj.demo.rentalapi.model.SyncContract;
import com.orhoj.demo.rentalapi.model.SyncContractItem;
import com.orhoj.demo.rentalapi.model.SyncCustomer;
import com.orhoj.demo.rentalapi.model.SyncDepot;
import com.orhoj.demo.rentalapi.model.SynchronizationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class HireToRentalMapper {

    private static final Logger log = LoggerFactory.getLogger(HireToRentalMapper.class);

    private final ZoneId serverZone;
    private final Clock clock;

    @Autowired
    public HireToRentalMapper(@Value("${hire-manager.time-zone:Europe/London}") String timeZone) {
        this(ZoneId.of(timeZone), Clock.systemUTC());
    }

    HireToRentalMapper(ZoneId serverZone, Clock clock) {
        this.serverZone = serverZone;
        this.clock = clock;
    }

    public List<SynchronizationRequest> mapAll(
            List<Machine> machines,
            List<Hire> hires,
            List<Account> accounts) {

        // Deduplicate machines by normalized stockNumber (keeping latest seen)
        Collection<Machine> uniqueMachines = machines.stream()
                .filter(m -> !m.getStockNumber().isBlank())
                .collect(Collectors.toMap(
                        m -> normalizeStockNumber(m.getStockNumber()),
                        Function.identity(),
                        (a, b) -> b,
                        LinkedHashMap::new))
                .values();

        // Deduplicate hires by hireId (keeping latest seen)
        Collection<Hire> uniqueHires = hires.stream()
                .collect(Collectors.toMap(
                        Hire::getHireId,
                        Function.identity(),
                        (a, b) -> b,
                        LinkedHashMap::new))
                .values();

        // Merge function is provided as pagination does not guarantee us anything about sorting, and
        // if data changes could result in the same account being returned multiple times.
        Map<String, Account> accountsByNo = accounts.stream()
                .collect(Collectors.toMap(Account::getAccountNo, Function.identity(), (a, b) -> b));

        // Index active (non-quote, non-cancelled) hire lines by normalized stockNumber
        Map<String, List<HireWithLine>> linesByStockNumber = uniqueHires.stream()
                .filter(h -> h.getKind() == Hire.KindEnum.HIRE)
                .filter(h -> h.getState() != Hire.StateEnum.CANCELLED)
                .flatMap(hire -> hire.getLines().stream()
                        .map(line -> new HireWithLine(hire, line)))
                .filter(hwl -> !hwl.line().getStockNumber().isBlank())
                .collect(Collectors.groupingBy(hwl -> normalizeStockNumber(hwl.line().getStockNumber())));

        List<SynchronizationRequest> requests = new ArrayList<>();

        Set<String> validStockNumbers = uniqueMachines.stream()
                .map(m -> normalizeStockNumber(m.getStockNumber()))
                .collect(Collectors.toSet());
        linesByStockNumber.forEach((stock, orphanedLines) -> {
            if (!validStockNumbers.contains(stock)) {
                orphanedLines.forEach(hwl -> log.warn(
                        "Skipping hire line for unknown stockNumber={} hireNumber={} lineNo={}",
                        stock, hwl.hire().getHireNumber(), hwl.line().getLineNo()));
            }
        });

        for (Machine machine : uniqueMachines) {
            if (machine.getFleet() != Machine.FleetEnum.HIRE) {
                log.info("Skipping non-hire machine stockNumber={} fleet={}",
                        machine.getStockNumber(), machine.getFleet());
                continue;
            }

            String normalizedStock = normalizeStockNumber(machine.getStockNumber());
            List<HireWithLine> machineLines = linesByStockNumber.getOrDefault(normalizedStock, List.of());

            SyncAsset asset = mapAsset(machine, machineLines, accountsByNo);
            SynchronizationRequest request = new SynchronizationRequest();
            request.setAsset(asset);
            requests.add(request);
        }

        return requests;
    }

    private SyncAsset mapAsset(
            Machine machine,
            List<HireWithLine> hireLines,
            Map<String, Account> accountsByNo) {

        SyncAsset asset = new SyncAsset();

        // Prefer SERIAL_NUMBER when available, fall back to EXTERNAL_REFERENCE (stockNumber)
        if (machine.getSerialNumber() != null && !machine.getSerialNumber().isBlank()) {
            asset.setIdentifier(machine.getSerialNumber().trim());
            asset.setIdentifierType("SERIAL_NUMBER");
        } else {
            asset.setIdentifier(normalizeStockNumber(machine.getStockNumber()));
            asset.setIdentifierType("EXTERNAL_REFERENCE");
        }

        List<SyncContractItem> contractItems = new ArrayList<>();
        for (HireWithLine hwl : hireLines) {
            SyncContractItem item = mapContractItem(hwl.hire(), hwl.line(), accountsByNo);
            if (item != null) {
                contractItems.add(item);
            }
        }

        asset.setContractItems(contractItems);
        asset.setStatus(deriveAssetStatus(machine, hireLines));
        return asset;
    }

    private SyncContractItem mapContractItem(
            Hire hire,
            HireLine line,
            Map<String, Account> accountsByNo) {

        String accountNo = hire.getCustomer() != null ? hire.getCustomer().getAccountNo() : null;
        Account account = accountNo != null ? accountsByNo.get(accountNo) : null;

        if (account != null && account.getAccountType() == Account.AccountTypeEnum.INTERNAL) {
            log.info("Skipping internal hire hireNumber={} accountNo={}", hire.getHireNumber(), accountNo);
            return null;
        }

        LocalDate effectiveStartDate = line.getStartDate() != null ? line.getStartDate() : hire.getStartDate();
        if (effectiveStartDate == null) {
            log.warn("Skipping hire line without startDate hireNumber={} lineNo={}",
                    hire.getHireNumber(), line.getLineNo());
            return null;
        }

        LocalTime effectiveStartTime = parseTimeOrDefault(
                line.getStartTime() != null ? line.getStartTime() : hire.getStartTime(),
                LocalTime.MIN);
        OffsetDateTime onRentDate = toServerOffsetDateTime(effectiveStartDate, effectiveStartTime);

        // Off-rent date: prefer actual returnedDate, else line dueBackDate, else hire dueBackDate (null = open-ended)
        LocalDate effectiveEndDate = line.getReturnedDate() != null
                ? line.getReturnedDate()
                : (line.getDueBackDate() != null ? line.getDueBackDate() : hire.getDueBackDate());

        OffsetDateTime offRentDate = null;
        if (effectiveEndDate != null) {
            String rawEndTime = line.getReturnedDate() != null
                    ? line.getReturnedTime()
                    : (line.getDueBackTime() != null ? line.getDueBackTime() : hire.getDueBackTime());
            LocalTime effectiveEndTime = parseTimeOrDefault(rawEndTime, LocalTime.of(23, 59, 59));
            offRentDate = toServerOffsetDateTime(effectiveEndDate, effectiveEndTime);
        }

        SyncContractItem item = new SyncContractItem();
        // TODO Must this be globally unique?
        item.setExternalReference(hire.getHireNumber() + "-" + line.getLineNo());
        item.setOnRentDate(onRentDate);
        item.setOffRentDate(offRentDate);
        item.setDeliveryAddress(mapAddress(hire.getSiteAddress()));
        item.setContract(mapContract(hire, account));
        item.setRentalRate(mapRentalRate(line, account));

        return item;
    }

    private SyncContract mapContract(Hire hire, Account account) {
        SyncContract contract = new SyncContract();
        // TODO Must this be globally unique?
        contract.setExternalReference(String.valueOf(hire.getHireId()));
        contract.setContractNumber(hire.getHireNumber());
        contract.setOrderNumber(hire.getOrderRef());
        contract.setTimeZone(serverZone.getId());

        LocalTime startTime = parseTimeOrDefault(hire.getStartTime(), LocalTime.MIN);
        contract.setStartDate(toServerOffsetDateTime(hire.getStartDate(), startTime));

        if (hire.getDueBackDate() != null) {
            LocalTime endTime = parseTimeOrDefault(hire.getDueBackTime(), LocalTime.of(23, 59, 59));
            contract.setEndDate(toServerOffsetDateTime(hire.getDueBackDate(), endTime));
        }

        contract.setStatus(mapContractStatus(hire.getState()));
        contract.setCustomer(mapCustomer(hire, account));

        if (hire.getDepotCode() != null && !hire.getDepotCode().isBlank()) {
            SyncDepot depot = new SyncDepot();
            depot.setIdentifier(hire.getDepotCode());
            depot.setIdentifierType("EXTERNAL_REFERENCE");
            contract.setHiringDepot(depot);
        }

        return contract;
    }

    private SyncCustomer mapCustomer(Hire hire, Account account) {
        SyncCustomer customer = new SyncCustomer();
        String accountNo = account != null ? account.getAccountNo() : hire.getCustomer().getAccountNo();
        String name = account != null ? account.getName() : hire.getCustomer().getName();

        customer.setExternalReference(accountNo);
        customer.setName(name != null && !name.isBlank() ? name : accountNo);
        customer.setType(mapCustomerType(account != null ? account.getAccountType() : null));

        if (account != null) {
            customer.setAddress(mapAddress(account.getAddress()));
            if (account.getContact() != null) {
                customer.setEmail(account.getContact().getEmail());
                customer.setPhone(account.getContact().getTelephone());
                customer.setFax(account.getContact().getFax());
            }
        }
        return customer;
    }

    private RentalRateRepresentation mapRentalRate(HireLine line, Account account) {
        if (line.getRatePeriod() == null) {
            return null;
        }
        RentalRateRepresentation rate = new RentalRateRepresentation();
        rate.setInterval(switch (line.getRatePeriod()) {
            case HOUR -> "HOURLY";
            case DAY -> "DAILY";
            case WEEK -> "WEEKLY";
            case MONTH -> "MONTHLY";
            case FIXED -> "FIXED";
        });

        if (line.getUnitPrice() != null) {
            rate.setUnitPrice(line.getUnitPrice());
        }
        if (account != null && account.getCurrencyCode() != null) {
            rate.setCurrency(account.getCurrencyCode());
        }
        return rate;
    }

    private AddressRepresentation mapAddress(Address source) {
        if (source == null) {
            return null;
        }
        AddressRepresentation target = new AddressRepresentation();
        target.setAddress(source.getLine1());
        target.setCity(source.getCity());
        target.setPostalCode(source.getPostcode());
        target.setCountry(source.getCountry());
        return target;
    }

    private String mapContractStatus(Hire.StateEnum state) {
        if (state == null) {
            return "OPEN";
        }
        return switch (state) {
            case CONFIRMED -> "OPEN";
            case UNCONFIRMED -> "RESERVED";
            case CANCELLED -> "CANCELLED";
            case CLOSED -> "COMPLETED";
        };
    }

    /**
     * Derives Trackunit asset status from hire lines rather than trusting machine.state blindly.
     */
    String deriveAssetStatus(Machine machine, List<HireWithLine> hireLines) {
        if (machine.getState() == Machine.StateEnum.SOLD || machine.getDisposedOn() != null) {
            // Null will remove the asset data in Trackunit.
            return null;
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        boolean hasFutureReservation = false;

        for (HireWithLine hwl : hireLines) {
            Hire hire = hwl.hire();
            HireLine line = hwl.line();

            if (hire.getState() == Hire.StateEnum.CLOSED || line.getReturnedDate() != null) {
                continue;
            }

            LocalDate startDate = line.getStartDate() != null ? line.getStartDate() : hire.getStartDate();
            OffsetDateTime start = toServerOffsetDateTime(
                    startDate, parseTimeOrDefault(line.getStartTime(), LocalTime.MIN));

            if (!now.isBefore(start) && hire.getState() == Hire.StateEnum.CONFIRMED) {
                if (Boolean.TRUE.equals(line.getPickupBooked())) {
                    return "PICK_UP_READY";
                }
                return "ON_RENT";
            }

            if (now.isBefore(start) || hire.getState() == Hire.StateEnum.UNCONFIRMED) {
                hasFutureReservation = true;
            }
        }

        if (hasFutureReservation) {
            return "RESERVED";
        }

        if (machine.getState() == Machine.StateEnum.IN_WORKSHOP) {
            return "IN_REPAIR";
        }

        return "AVAILABLE";
    }

    private SyncCustomer.TypeEnum mapCustomerType(Account.AccountTypeEnum accountType) {
        if (accountType == null) {
            return SyncCustomer.TypeEnum.OTHER;
        }
        return switch (accountType) {
            case TRADE -> SyncCustomer.TypeEnum.CONTRACTOR;
            case PRIVATE, SUPPLIER, INTERNAL -> SyncCustomer.TypeEnum.OTHER;
        };
    }

    private static String normalizeStockNumber(String raw) {
        return raw.trim().toUpperCase();
    }

    private static LocalTime parseTimeOrDefault(String hhMm, LocalTime fallback) {
        if (hhMm == null || hhMm.isBlank()) {
            return fallback;
        }
        return LocalTime.parse(hhMm.trim());
    }

    private OffsetDateTime toServerOffsetDateTime(LocalDate date, LocalTime time) {
        // Converts naive server wall-clock time using the configured server ZoneId (e.g. Europe/London).
        // Applies +00:00 (GMT) in winter and +01:00 (BST) in summer; resolves autumn DST overlap deterministically to BST.
        return ZonedDateTime.of(LocalDateTime.of(date, time), serverZone).toOffsetDateTime();
    }

    record HireWithLine(Hire hire, HireLine line) {}
}
