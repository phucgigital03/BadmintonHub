package com.badmintonhub.escrow.controller;

import com.badmintonhub.escrow.dto.response.EscrowAccountResponse;
import com.badmintonhub.escrow.dto.response.EscrowTransactionResponse;
import com.badmintonhub.escrow.service.EscrowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * STAFF/ADMIN operational queues over the escrow ledger. All read-only: escrow itself is driven by Kafka
 * events, and the actual bank transfers (settlements / refunds) are executed manually by STAFF in
 * payment-service (Never-Violate #2). These endpoints just surface what is PENDING.
 */
@RestController
@RequestMapping("/api/escrow")
@RequiredArgsConstructor
public class EscrowController {

    private final EscrowService escrowService;

    @GetMapping("/settlements/pending")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public ResponseEntity<List<EscrowTransactionResponse>> pendingSettlements() {
        return ResponseEntity.ok(escrowService.pendingSettlements());
    }

    @GetMapping("/refunds/pending")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public ResponseEntity<List<EscrowTransactionResponse>> pendingRefunds() {
        return ResponseEntity.ok(escrowService.pendingRefunds());
    }

    @GetMapping("/{matchId}")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public ResponseEntity<EscrowAccountResponse> getByMatchId(@PathVariable UUID matchId) {
        return ResponseEntity.ok(escrowService.getByMatchId(matchId));
    }
}
