package org.pronto.pronto.template;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RenderedMessage {
    String title;
    String body;
    String deepLink;
}
