package com.orhoj.trackunit.demo.sync;

import com.orhoj.demo.hiremanager.api.HiresApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class HireManagerSyncRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(HireManagerSyncRunner.class);
    private final ApplicationContext context;

    public HireManagerSyncRunner(HiresApi hiresApi, ApplicationContext context) {
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        boolean isDryRun = args.containsOption("dry-run");
        log.info("Starting Hire Sync (dryRun={})", isDryRun);

        // TODO Fetch data from hire manager API

        // TODO Map to Trackunit SynchronizationRequest objects.

        // TODO Output dry-run JSONL file.
        if (isDryRun) {
            // Exit straight after dry-run.
            SpringApplication.exit(context, () -> 0);
        }
    }
}
