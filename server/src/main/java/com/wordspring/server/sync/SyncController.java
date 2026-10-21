package com.wordspring.server.sync;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.wordspring.server.sync.SyncDtos.PullResponse;
import com.wordspring.server.sync.SyncDtos.PushRequest;
import com.wordspring.server.sync.SyncDtos.PushResponse;

@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/push")
    public PushResponse push(@AuthenticationPrincipal Long userId,
                             @Valid @RequestBody PushRequest request) {
        return syncService.push(userId, request);
    }

    @GetMapping("/pull")
    public PullResponse pull(@AuthenticationPrincipal Long userId,
                             @RequestParam(defaultValue = "0") long since) {
        return syncService.pull(userId, since);
    }
}
