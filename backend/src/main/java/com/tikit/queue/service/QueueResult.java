package com.tikit.queue.service;

import com.tikit.queue.dto.response.QueueEntryResponseDTO;

// created = 이번 요청으로 새로 줄을 섰으면 true (201), 기존 항목이면 false (200)
public record QueueResult(QueueEntryResponseDTO entry, boolean created) {
}
