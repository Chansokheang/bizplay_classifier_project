package com.api.bizplay_chatbot.kakao.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One element inside {@code template.outputs}. Kakao supports many output
 * kinds ({@code simpleText}, {@code simpleImage}, {@code basicCard}, …);
 * we only emit {@code simpleText} so other fields stay null and are dropped
 * by {@link JsonInclude.Include#NON_NULL}.
 *
 * <p>No {@code @JsonNaming} strategy: Kakao's Skill API expects the key
 * {@code simpleText} on the wire. Snake_case would emit {@code simple_text}
 * which Openbuilder rejects with "Invalid skill server response".
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OutputComponent {
    private SimpleText simpleText;
}
