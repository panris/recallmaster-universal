package com.recallmaster.universal.task;

import com.recallmaster.universal.model.EvaluationRun;
import com.recallmaster.universal.model.RunStatus;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ScheduledRunService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledRunService.class);

    private final EvaluationRunService evaluationRunService;
    private final Map<String, ScheduledConfig> scheduledRuns = new ConcurrentHashMap<>();

    public ScheduledRunService(EvaluationRunService evaluationRunService) {
        this.evaluationRunService = evaluationRunService;
    }

    public String schedule(ScheduledConfig config) {
        String scheduleId = config.id();
        scheduledRuns.put(scheduleId, config);
        log.info("Scheduled run registered: {} (cron: {})", scheduleId, config.cron());
        return scheduleId;
    }

    public void cancelSchedule(String scheduleId) {
        scheduledRuns.remove(scheduleId);
        log.info("Scheduled run cancelled: {}", scheduleId);
    }

    public Map<String, ScheduledConfig> listSchedules() {
        return Map.copyOf(scheduledRuns);
    }

    @Scheduled(fixedDelay = 60_000) // Check every minute
    public void checkAndExecute() {
        for (ScheduledConfig config : scheduledRuns.values()) {
            if (config.shouldRunNow()) {
                log.info("Executing scheduled run: {}", config.id());
                try {
                    EvaluationRunRequest request = new EvaluationRunRequest(
                            config.connectorName(), config.topK(), config.cases());
                    evaluationRunService.start(request);
                    config.markLastRun();
                } catch (Exception ex) {
                    log.error("Scheduled run {} failed: {}", config.id(), ex.getMessage());
                }
            }
        }
    }

    public record ScheduledConfig(
            String id,
            String connectorName,
            int topK,
            java.util.List<com.recallmaster.universal.model.EvaluationCase> cases,
            String cron,
            long lastRunEpochMs
    ) {
        public ScheduledConfig {
            id = id != null ? id : java.util.UUID.randomUUID().toString();
        }

        public boolean shouldRunNow() {
            if (lastRunEpochMs == 0) return true;
            // Simple interval-based: run every N minutes
            long intervalMs = parseCronToIntervalMs(cron);
            return System.currentTimeMillis() - lastRunEpochMs >= intervalMs;
        }

        public ScheduledConfig markLastRun() {
            return new ScheduledConfig(id, connectorName, topK, cases, cron, System.currentTimeMillis());
        }

        private long parseCronToIntervalMs(String cronExpr) {
            // Simple: "every Nm" format → N minutes
            if (cronExpr != null && cronExpr.startsWith("every ")) {
                String num = cronExpr.substring(6).replace("m", "").replace("h", "");
                long value = Long.parseLong(num.trim());
                if (cronExpr.contains("h")) return value * 3600_000;
                return value * 60_000;
            }
            // Default: 60 minutes
            return 3600_000;
        }
    }
}