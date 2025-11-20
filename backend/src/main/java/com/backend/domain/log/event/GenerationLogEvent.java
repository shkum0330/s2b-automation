package com.backend.domain.log.event;

import com.backend.domain.member.entity.Member;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class GenerationLogEvent {
    private final Member member;
    private final Object requestDto;   // GenerateElectronicRequest 또는 GenerateNonElectronicRequest
    private final Object responseDto;  // GenerateElectronicResponse 또는 GenerateNonElectronicResponse
    private final Throwable error;      // 성공 시 null
}
