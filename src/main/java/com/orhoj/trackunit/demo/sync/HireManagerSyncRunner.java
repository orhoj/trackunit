package com.orhoj.trackunit.demo.sync;

import com.orhoj.demo.hiremanager.model.Account;
import com.orhoj.demo.hiremanager.model.Hire;
import com.orhoj.demo.hiremanager.model.Machine;
import com.orhoj.demo.rentalapi.model.SynchronizationRequest;
import com.orhoj.trackunit.demo.mapper.HireToRentalMapper;
import com.orhoj.trackunit.demo.service.HireManagerClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
public class HireManagerSyncRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(HireManagerSyncRunner.class);

    private final HireManagerClientService clientService;
    private final HireToRentalMapper mapper;
    private final DryRunWriter dryRunWriter;
    private final ApplicationContext context;

    public HireManagerSyncRunner(
            HireManagerClientService clientService,
            HireToRentalMapper mapper,
            DryRunWriter dryRunWriter,
            ApplicationContext context) {
        this.clientService = clientService;
        this.mapper = mapper;
        this.dryRunWriter = dryRunWriter;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        boolean isDryRun = args.containsOption("dry-run");
        log.info("Starting Hire Sync (dryRun={})", isDryRun);

        List<Machine> machines = clientService.fetchAllMachines();
        List<Hire> hires = clientService.fetchAllHires();
        List<Account> accounts = clientService.fetchAllAccounts();

        log.info("Fetched: machines={} hires={} accounts={}",
                machines.size(), hires.size(), accounts.size());

        List<SynchronizationRequest> syncRequests = mapper.mapAll(machines, hires, accounts);
        log.info("Mapped {} SynchronizationRequest(s)", syncRequests.size());

        if (isDryRun) {
            Path outputPath = Path.of("dry-run-output.jsonl");
            dryRunWriter.write(outputPath, syncRequests);
            log.info("Wrote {} synchronization request(s) to {}", syncRequests.size(), outputPath.toAbsolutePath());

            SpringApplication.exit(context, () -> 0);
        } else {
            // TODO Implement live POST /v1/synchronization call (with all the bells and whistles [retry, rate limit
            // handling etc.]).
            log.error("Live sync mode has not been implemented yet. Run with --dry-run");
        }
    }
}
