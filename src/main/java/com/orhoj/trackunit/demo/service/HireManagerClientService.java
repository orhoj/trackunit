package com.orhoj.trackunit.demo.service;

import com.orhoj.demo.hiremanager.api.AccountsApi;
import com.orhoj.demo.hiremanager.api.HiresApi;
import com.orhoj.demo.hiremanager.api.MachinesApi;
import com.orhoj.demo.hiremanager.model.Account;
import com.orhoj.demo.hiremanager.model.AccountPage;
import com.orhoj.demo.hiremanager.model.Hire;
import com.orhoj.demo.hiremanager.model.HirePage;
import com.orhoj.demo.hiremanager.model.Machine;
import com.orhoj.demo.hiremanager.model.MachinePage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class HireManagerClientService {

    private static final Logger log = LoggerFactory.getLogger(HireManagerClientService.class);
    private static final int PAGE_SIZE = 50; // Max allowed by spec

    private final MachinesApi machinesApi;
    private final HiresApi hiresApi;
    private final AccountsApi accountsApi;

    public HireManagerClientService(
            MachinesApi machinesApi,
            HiresApi hiresApi,
            AccountsApi accountsApi) {
        this.machinesApi = machinesApi;
        this.hiresApi = hiresApi;
        this.accountsApi = accountsApi;
    }

    // TODO: Refactor - the pagination pattern is repeated, extract to generic helper.
    public List<Machine> fetchAllMachines() {
        List<Machine> machines = new ArrayList<>();
        int page = 1;
        boolean hasMore;

        do {
            MachinePage response = machinesApi.listMachines(page, PAGE_SIZE, null, null);
            List<Machine> items = response.getItems() != null ? response.getItems() : List.of();
            machines.addAll(items);
            log.trace("Fetched {} machines in page {}", items.size(), page);

            hasMore = !Boolean.TRUE.equals(response.getLastPage()) && !items.isEmpty();
            page++;
        } while (hasMore);

        log.info("Fetched {} machine(s)", machines.size());
        return machines;
    }

    public List<Hire> fetchAllHires() {
        List<Hire> hires = new ArrayList<>();
        int page = 1;
        boolean hasMore;

        do {
            HirePage response = hiresApi.listHires(page, PAGE_SIZE, null, null, null);
            List<Hire> items = response.getItems() != null ? response.getItems() : List.of();
            hires.addAll(items);
            log.trace("Fetched {} hires in page {}", items.size(), page);

            hasMore = !Boolean.TRUE.equals(response.getLastPage()) && !items.isEmpty();
            page++;
        } while (hasMore);

        log.info("Fetched {} hire(s)", hires.size());
        return hires;
    }

    public List<Account> fetchAllAccounts() {
        List<Account> accounts = new ArrayList<>();
        int page = 1;
        boolean hasMore;

        do {
            AccountPage response = accountsApi.listAccounts(page, PAGE_SIZE, null);
            List<Account> items = response.getItems() != null ? response.getItems() : List.of();
            accounts.addAll(items);
            log.trace("Fetched {} accounts in page {}", items.size(), page);

            hasMore = !Boolean.TRUE.equals(response.getLastPage()) && !items.isEmpty();
            page++;
        } while (hasMore);

        log.info("Fetched {} account(s)", accounts.size());
        return accounts;
    }
}
