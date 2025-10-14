package ca.admin.delivermore.views.restaurants;

import ca.admin.delivermore.collector.data.service.RestaurantRepository;
import ca.admin.delivermore.data.report.RestPayoutSummary;
import ca.admin.delivermore.views.MainLayout;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.Main;
import com.vaadin.flow.component.html.NativeLabel;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.annotation.security.RolesAllowed;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

@PageTitle("Restaurant Payouts")
@Route(value = "restaurantpayouts", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class RestPayoutView extends Main {

    private VerticalLayout detailsLayout = new VerticalLayout();
    private Logger log = LoggerFactory.getLogger(RestPayoutView.class);

    LocalDate defaultStartDate;
    LocalDate defaultEndDate;

    Boolean validStartDate = Boolean.TRUE;
    DatePicker periodStartDate = new DatePicker();
    NativeLabel periodStartDateStatus = new NativeLabel();

    RestPayoutSummary restPayoutSummary;

    RestaurantRepository restaurantRepository;

    public RestPayoutView(@Autowired RestaurantRepository restaurantRepository) {
        this.restaurantRepository = restaurantRepository;
        periodStartDate.setLabel("Select start date:");
        configureDatePicker();

        buildRestPayoutDetails();
        add(getToolbar(), getContent());

    }

    private HorizontalLayout getToolbar() {
        HorizontalLayout toolbar = new HorizontalLayout(periodStartDate, periodStartDateStatus);
        toolbar.setPadding(true);
        toolbar.setAlignItems(FlexComponent.Alignment.BASELINE);
        toolbar.addClassName("toolbar");
        return toolbar;
    }

    private Component getContent() {
        VerticalLayout mainLayout = new VerticalLayout();
        mainLayout.add(detailsLayout);
        detailsLayout.setSizeFull();
        return mainLayout;
    }


    private void configureDatePicker() {
        LocalDate defaultDate = LocalDate.parse("2022-08-14");

        //get lastWeek as the default for the range picker
        LocalDate nowDate = LocalDate.now();
        LocalDate prevSun = nowDate.with(TemporalAdjusters.previous(DayOfWeek.SUNDAY));
        prevSun = nowDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        //KEB - 2025-10-14 - subtract 2 weeks for rest payout as we now hold back 1 week
        LocalDate startOfLastWeek = prevSun.minusWeeks(2);
        LocalDate endOfLastWeek = startOfLastWeek.plusDays(6);
        defaultStartDate = startOfLastWeek;
        defaultEndDate = endOfLastWeek;

        periodStartDate.setMin(defaultDate);
        periodStartDate.setValue(startOfLastWeek);
        periodStartDate.addValueChangeListener(e -> {
            defaultStartDate = periodStartDate.getValue();
            defaultEndDate = defaultStartDate.plusDays(6);
        });

        periodStartDate.setHelperText("Sundays only");

        Binder<PeriodStart> binder = new Binder<>(PeriodStart.class);
        binder.forField(periodStartDate)
                .withValidator(localDate -> {
            DayOfWeek dayOfWeek = localDate.getDayOfWeek();
            boolean validWeekDay = dayOfWeek.equals(DayOfWeek.SUNDAY);
            //validStartDate = validWeekDay;
            return validWeekDay;
        }, "Invalid start date selected. Please select a Sunday")
                .withValidationStatusHandler(status -> {
                    validStartDate = !status.isError();
                    periodStartDateStatus.setText(status
                            .getMessage().orElse(""));
                    periodStartDateStatus.setVisible(status.isError());
                    periodStartDate.setInvalid(status.isError());
                    buildRestPayoutDetails();
                })
                .bind(PeriodStart::getStartDate,
                PeriodStart::setStartDate);


    }

    private void buildRestPayoutDetails() {
        detailsLayout.removeAll();

        log.info("buildRestPayoutDetails: defaultStart:" + defaultStartDate + " defaultEnd:" + defaultEndDate + " validStartDate:" + validStartDate);

        if(validStartDate){
            restPayoutSummary = new RestPayoutSummary(defaultStartDate,defaultEndDate, Boolean.TRUE);
            //log.info("buildRestPayoutDetails: summary owing:" + restPayoutSummary.getOwingToVendor());
            detailsLayout.add(restPayoutSummary.getMainLayout());
        }

    }

    private class PeriodStart {
        private LocalDate startDate;

        public LocalDate getStartDate() {
            return startDate;
        }

        public void setStartDate(LocalDate startDate) {
            this.startDate = startDate;
        }
    }

}
