package ru.sklad.app.service;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/** Immutable order snapshot carried between views in the current Vaadin session. */
public record PendingSupply(Long orderId, List<Line> lines) implements Serializable {
    public PendingSupply { lines = List.copyOf(lines); }

    public record Line(Long orderItemId, Long productId, String name, String unit,
                       BigDecimal quantity) implements Serializable {}
}
