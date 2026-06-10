package com.badmintonhub.court.service;

import com.badmintonhub.court.dto.request.CreateCourtRequest;
import com.badmintonhub.court.dto.response.CourtResponse;

import java.util.List;
import java.util.UUID;

public interface CourtService {

    CourtResponse addCourt(UUID clubId, CreateCourtRequest req);

    List<CourtResponse> listCourts(UUID clubId);

    /** STAFF/ADMIN marks a slot BLOCKED (e.g. maintenance), recording who blocked it. */
    void blockSlot(UUID slotId, UUID blockedBy);
}
