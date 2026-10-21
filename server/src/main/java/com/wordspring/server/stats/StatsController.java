package com.wordspring.server.stats;

import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.wordspring.server.sync.DailySummaryRepository;
import com.wordspring.server.sync.SyncDtos.DailySummary;

@RestController
@RequestMapping("/api/v1/stats")
public class StatsController {

    private final DailySummaryRepository summaries;

    public StatsController(DailySummaryRepository summaries) {
        this.summaries = summaries;
    }

    @GetMapping("/daily")
    public List<DailySummary> daily(@AuthenticationPrincipal Long userId,
                                    @RequestParam String from,
                                    @RequestParam String to) {
        return summaries
                .findByUserIdAndLocalDateBetweenOrderByLocalDateAsc(userId, from, to)
                .stream()
                .map(s -> new DailySummary(s.getLocalDate(), s.getReviewedCount(),
                        s.getNewCount(), s.getCorrectCount(), s.getStudySeconds()))
                .toList();
    }
}
