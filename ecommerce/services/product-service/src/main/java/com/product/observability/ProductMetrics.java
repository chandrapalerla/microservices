package com.product.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class ProductMetrics {

    private final Counter productCreated;
    private final Counter stockDeducted;
    private final Counter stockRestored;
    private final Counter outOfStock;

    public ProductMetrics(MeterRegistry registry) {
        this.productCreated = Counter.builder("product.created")
                .description("Total products created")
                .register(registry);
        this.stockDeducted = Counter.builder("product.stock.deducted")
                .description("Total stock deduction operations")
                .register(registry);
        this.stockRestored = Counter.builder("product.stock.restored")
                .description("Total stock restoration operations")
                .register(registry);
        this.outOfStock = Counter.builder("product.out.of.stock")
                .description("Number of times a product went out of stock")
                .register(registry);
    }

    public void incrementProductCreated()  { productCreated.increment(); }
    public void incrementStockDeducted()   { stockDeducted.increment(); }
    public void incrementStockRestored()   { stockRestored.increment(); }
    public void incrementOutOfStock()      { outOfStock.increment(); }
}
