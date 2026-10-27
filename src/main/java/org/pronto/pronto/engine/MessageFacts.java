package org.pronto.pronto.engine;

import lombok.Builder;
import lombok.Value;
import org.pronto.pronto.entity.MessageTemplate;

/** The message being decided, as seen by rule expressions (`message.campaignCode`). */
@Value
@Builder
public class MessageFacts {
    String type;
    String templateCode;
    String campaignCode;

    static MessageFacts from(MessageTemplate template) {
        return MessageFacts.builder()
                .type(template.getType().name())
                .templateCode(template.getCode())
                .campaignCode(template.getCampaign() == null ? null : template.getCampaign().getCode())
                .build();
    }
}
