package com.tikit.performance.dto.response;


import com.tikit.performance.dto.SeatTemplateSeatDTO;

import java.util.List;

public record SeatTemplateResponseDTO(Long templateId, String name,
                                      List<SeatTemplateSeatDTO> seats) {}

