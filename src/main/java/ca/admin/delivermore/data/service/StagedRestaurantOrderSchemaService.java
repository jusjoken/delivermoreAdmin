package ca.admin.delivermore.data.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service("adminStagedRestaurantOrderSchemaService")
public class StagedRestaurantOrderSchemaService {

    private static final Logger log = LoggerFactory.getLogger(StagedRestaurantOrderSchemaService.class);

    private final JdbcTemplate jdbcTemplate;

    public StagedRestaurantOrderSchemaService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void widenApprovalStatusColumn() {
        try {
            jdbcTemplate.execute("ALTER TABLE staged_restaurant_order MODIFY COLUMN approval_status VARCHAR(32) NOT NULL");
            log.info("widenApprovalStatusColumn: ensured staged_restaurant_order.approval_status can store MISSED");
        } catch (RuntimeException ex) {
            log.warn("widenApprovalStatusColumn: unable to update staged_restaurant_order.approval_status: {}", ex.getMessage(), ex);
        }
    }
}