package com.tikit.performance.dto;


import java.util.List;

public record SeatTemplateResponseDTO(Long templateId, String name,
                                      List<SeatTemplateSeatDTO> seats) {}

