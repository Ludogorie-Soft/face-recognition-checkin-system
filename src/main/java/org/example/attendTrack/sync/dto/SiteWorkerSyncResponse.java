package org.example.garant.sync.dto;

import java.util.UUID;

public record SiteWorkerSyncResponse(
        UUID id,
        String name,
        double[] descriptor
) {}
