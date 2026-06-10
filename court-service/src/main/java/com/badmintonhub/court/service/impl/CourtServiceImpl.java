package com.badmintonhub.court.service.impl;

import com.badmintonhub.common.exception.ResourceNotFoundException;
import com.badmintonhub.court.dto.request.CreateCourtRequest;
import com.badmintonhub.court.dto.response.CourtResponse;
import com.badmintonhub.court.entity.Club;
import com.badmintonhub.court.entity.Court;
import com.badmintonhub.court.entity.TimeSlot;
import com.badmintonhub.court.entity.enums.SlotStatus;
import com.badmintonhub.court.repository.ClubRepository;
import com.badmintonhub.court.repository.CourtRepository;
import com.badmintonhub.court.repository.TimeSlotRepository;
import com.badmintonhub.court.service.CourtService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CourtServiceImpl implements CourtService {

    private final ClubRepository clubRepository;
    private final CourtRepository courtRepository;
    private final TimeSlotRepository timeSlotRepository;

    @Override
    @Transactional
    public CourtResponse addCourt(UUID clubId, CreateCourtRequest req) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new ResourceNotFoundException("CLUB_NOT_FOUND", "Không tìm thấy CLB"));

        Court court = new Court();
        court.setClub(club);
        court.setCourtNumber(req.courtNumber());
        court.setSport(req.sport());
        court.setType(req.type());
        return toResponse(courtRepository.save(court));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourtResponse> listCourts(UUID clubId) {
        return courtRepository.findByClub_IdAndIsActiveTrueOrderByCourtNumber(clubId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void blockSlot(UUID slotId, UUID blockedBy) {
        TimeSlot slot = timeSlotRepository.findById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("SLOT_NOT_FOUND", "Không tìm thấy ô thời gian"));
        slot.setStatus(SlotStatus.BLOCKED);
        slot.setBlockedBy(blockedBy);
        timeSlotRepository.save(slot);
    }

    private CourtResponse toResponse(Court c) {
        return new CourtResponse(
                c.getId(), c.getClub().getId(), c.getCourtNumber(), c.getSport(), c.getType(), c.isActive());
    }
}
