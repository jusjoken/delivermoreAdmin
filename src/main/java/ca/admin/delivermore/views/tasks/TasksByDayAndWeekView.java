package ca.admin.delivermore.views.tasks;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.HeaderRow;
import com.vaadin.flow.component.html.NativeLabel;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import ca.admin.delivermore.collector.data.service.RestClientService;
import ca.admin.delivermore.collector.data.service.TaskDetailRepository;
import ca.admin.delivermore.data.report.TasksForMonth;
import ca.admin.delivermore.data.report.TasksForWeek;
import ca.admin.delivermore.data.service.TasksForWeekRepository;
import ca.admin.delivermore.views.MainLayout;
import ca.admin.delivermore.views.UIUtilities;
import jakarta.annotation.security.RolesAllowed;

@PageTitle("Tasks Report (by Day/Week/Month)")
@Route(value = "tasksbydayandweek", layout = MainLayout.class)
@RolesAllowed({"ADMIN","MANAGER"})
public class TasksByDayAndWeekView extends VerticalLayout {
    private final TaskDetailRepository taskDetailRepository;
    private final RestClientService restClientService;
    private final Grid<TasksForWeek> grid = new Grid<>();
    private final TasksForWeekRepository tasksForWeekRepository;

    private HeaderRow hRowRecords;
    private HeaderRow hRowAverages;
    private HeaderRow hRowMonthRecord;
    private HeaderRow hRowMonthAverage;
    private Grid.Column<TasksForWeek> colName;
    private Grid.Column<TasksForWeek> colSunday;
    private Grid.Column<TasksForWeek> colMonday;
    private Grid.Column<TasksForWeek> colTuesday;
    private Grid.Column<TasksForWeek> colWednesday;
    private Grid.Column<TasksForWeek> colThursday;
    private Grid.Column<TasksForWeek> colFriday;
    private Grid.Column<TasksForWeek> colSaturday;
    private Grid.Column<TasksForWeek> colWeek;

    private Grid.Column<TasksForMonth> colMonthName;
    private Grid.Column<TasksForMonth> colMonthCount;


    private final Grid<TasksForMonth> gridMonths = new Grid<>();
    private final NativeLabel countLabel = new NativeLabel();
    private final List<TasksForWeek> tasksForWeeks = new ArrayList<>();
    private final Logger log = LoggerFactory.getLogger(TasksByDayAndWeekView.class);
    private final TasksForWeek recordTasksForWeek = new TasksForWeek(TasksForWeek.TasksForWeekType.RECORD);
    private final TasksForWeek sumTasksForWeek = new TasksForWeek(TasksForWeek.TasksForWeekType.SUM);
    private final TasksForWeek averageTasksForWeek = new TasksForWeek(TasksForWeek.TasksForWeekType.AVERAGE);

    private final List<TasksForMonth> tasksForMonths = new ArrayList<>();
    private final TasksForMonth recordTasksForMonth = new TasksForMonth(TasksForMonth.TasksForMonthType.RECORD);
    private final TasksForMonth sumTasksForMonth = new TasksForMonth(TasksForMonth.TasksForMonthType.SUM);
    private final TasksForMonth averageTasksForMonth = new TasksForMonth(TasksForMonth.TasksForMonthType.AVERAGE);

    public TasksByDayAndWeekView(TaskDetailRepository taskDetailRepository, RestClientService restClientService, TasksForWeekRepository tasksForWeekRepository) {
        this.taskDetailRepository = taskDetailRepository;
        this.restClientService = restClientService;
        this.tasksForWeekRepository = tasksForWeekRepository;
        //addClassNames("tasksbydayandweek-view");
        configureGrid();
        configureGridMonths();
        // layout configuration
        setSizeFull();
        add(getToolbar(), grid, gridMonths);

        updateList();

    }

    private void configureGrid(){
        grid.removeAllColumns();
        //grid.setClassName("no-upload-grid-no");
        //grid.setClassName("task-record");
        colName = grid.addColumn(TasksForWeek::getWeekName)
                .setWidth("125px")
                .setHeader("Week")
                .setFrozen(true);
                //.getElement().getStyle().set("background-color", "yellow");
        colSunday = grid.addColumn(TasksForWeek::getDowCountSunday)
                .setTextAlign(ColumnTextAlign.END)
            .setPartNameGenerator(item -> Objects.equals(item.getDowCountSunday(), recordTasksForWeek.getDowCountSunday()) ? "record" : null)
                .setHeader("Sun");
        colMonday = grid.addColumn(TasksForWeek::getDowCountMonday)
                .setTextAlign(ColumnTextAlign.END)
            .setPartNameGenerator(item -> Objects.equals(item.getDowCountMonday(), recordTasksForWeek.getDowCountMonday()) ? "record" : null)
                .setHeader("Mon");
        colTuesday = grid.addColumn(TasksForWeek::getDowCountTuesday)
                .setTextAlign(ColumnTextAlign.END)
            .setPartNameGenerator(item -> Objects.equals(item.getDowCountTuesday(), recordTasksForWeek.getDowCountTuesday()) ? "record" : null)
                .setHeader("Tue");
        colWednesday = grid.addColumn(TasksForWeek::getDowCountWednesday)
                .setTextAlign(ColumnTextAlign.END)
            .setPartNameGenerator(item -> Objects.equals(item.getDowCountWednesday(), recordTasksForWeek.getDowCountWednesday()) ? "record" : null)
                .setHeader("Wed");
        colThursday = grid.addColumn(TasksForWeek::getDowCountThursday)
                .setTextAlign(ColumnTextAlign.END)
            .setPartNameGenerator(item -> Objects.equals(item.getDowCountThursday(), recordTasksForWeek.getDowCountThursday()) ? "record" : null)
                .setHeader("Thu");
        colFriday = grid.addColumn(TasksForWeek::getDowCountFriday)
                .setTextAlign(ColumnTextAlign.END)
            .setPartNameGenerator(item -> Objects.equals(item.getDowCountFriday(), recordTasksForWeek.getDowCountFriday()) ? "record" : null)
                .setHeader("Fri");
        colSaturday = grid.addColumn(TasksForWeek::getDowCountSaturday)
                .setTextAlign(ColumnTextAlign.END)
            .setPartNameGenerator(item -> Objects.equals(item.getDowCountSaturday(), recordTasksForWeek.getDowCountSaturday()) ? "record" : null)
                .setHeader("Sat");
        colWeek = grid.addColumn(TasksForWeek::getWeekCount)
                .setTextAlign(ColumnTextAlign.END)
            .setPartNameGenerator(item -> Objects.equals(item.getWeekCount(), recordTasksForWeek.getWeekCount()) ? "record" : null)
                .setHeader("Total");
        //grid.setColumnReorderingAllowed(false);
        //grid.getColumns().forEach(col -> col.setAutoWidth(true));
        //grid.addThemeVariants(GridVariant.LUMO_COLUMN_BORDERS);
        hRowRecords = grid.appendHeaderRow();
        hRowAverages = grid.appendHeaderRow();

    }

    private void configureGridMonths(){
        gridMonths.removeAllColumns();
        colMonthName = gridMonths.addColumn(TasksForMonth::getMonthName)
                .setWidth("150px")
                .setHeader("Month");
        colMonthCount = gridMonths.addColumn(TasksForMonth::getMonthCount)
                .setTextAlign(ColumnTextAlign.END)
                .setWidth("150px")
                .setHeader("Count");
        gridMonths.setWidth("350px");
        hRowMonthRecord = gridMonths.appendHeaderRow();
        hRowMonthAverage = gridMonths.appendHeaderRow();
    }

    private HorizontalLayout getToolbar() {

        // Fetch all entities and show
        final Button fetchTasks = new Button("Refresh",
                e -> updateList()
        );
        fetchTasks.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        // Fetch all entities and show
        final Button rebuildTasks = new Button("Rebuild All",
                e -> rebuildAll()
        );
        rebuildTasks.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout toolbar = new HorizontalLayout(fetchTasks, countLabel, rebuildTasks);
        toolbar.setAlignItems(FlexComponent.Alignment.BASELINE);
        toolbar.addClassName("toolbar");
        return toolbar;
    }

    private void updateList() {
        tasksForWeeks.clear();
        
        LocalDate defaultStartDate = LocalDate.parse("2022-08-14");
        
        TasksForWeek maxTaskWeek = tasksForWeekRepository.findFirstByOrderByStartDateDesc();
        LocalDate maxDate = maxTaskWeek == null ? defaultStartDate : maxTaskWeek.getStartDate();
        
        LocalDate startDate = maxDate;
        LocalDate endDate = LocalDate.now();
        log.info("updateList: start:" + startDate + " end:" + endDate + " max:" + maxDate);
        //Long taskCount = 0L;

        //build TasksForWeek list
        recordTasksForWeek.setStartDate(LocalDate.now());
        sumTasksForWeek.setStartDate(LocalDate.now());
        //sumTasksForWeek.setWeekCount(0L);
        sumTasksForWeek.clearCounters();
        averageTasksForWeek.setStartDate(LocalDate.now());
        //averageTasksForWeek.setWeekCount(0L);
        averageTasksForWeek.clearCounters();

        TasksForWeek tasksForWeek = new TasksForWeek();
        LocalDate firstDate = startDate;
        for (LocalDate date = startDate; date.isBefore(endDate.plusDays(1)); date = date.plusDays(1)) {
            //log.info("updateList: processing date:" + date);
            tasksForWeek.setEndDate(date);
            if (date.getDayOfWeek().equals(DayOfWeek.SUNDAY)) {
                //log.info("updateList: processing date:" + date + " found SUNDAY");
                //close out previous week if any
                if (!date.equals(startDate)) {
                    log.info("updateList: processing date:" + date + " found SUNDAY that is NOT the start date");
                    tasksForWeek.setStartDate(firstDate);
                    
                    //only full weeks get saved to the database
                    tasksForWeekRepository.save(tasksForWeek);
                }
                //log.info("updateList: processing date:" + date + " found SUNDAY - creating new TasksForWeek");
                tasksForWeek = new TasksForWeek();
                firstDate = date;
            }
            Long dayCount;
            if (date.equals(endDate)) {
                //Update today directly from tookan API
                dayCount = Long.valueOf(restClientService.getTaskCount(LocalDate.now(), LocalDate.now()));
                //log.info("updateList: count for today " + endDate + " updated from Tookan API:" + dayCount);
            } else {
                //log.info("updateList: processing date:" + date + " adding...");
                Date dateForRequest = Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
                dayCount = taskDetailRepository.findTaskCountByDate(dateForRequest);
            }
            tasksForWeek.add(date, dayCount);
            //taskCount = taskCount + dayCount;
        }
        //close out previous week if any
        log.info("updateList: close out week for firstDate: " + firstDate);
        tasksForWeek.setStartDate(firstDate);

        //save the last processed week
        tasksForWeekRepository.save(tasksForWeek);

        //load list from database
        tasksForWeeks.addAll(tasksForWeekRepository.findAll());
        
        //Loop through list to get record and sums for all weeks
        for (TasksForWeek item : tasksForWeeks) {
            //week record
            recordTasksForWeek.addWeekIfHigher(item.getWeekCountLong());
            //record per day
            recordTasksForWeek.addIfHigher(item);
            
            sumTasksForWeek.addToSum(item);
            //log.info("updateList: item: " + item);
            //log.info("updateList: sum : " + sumTasksForWeek);
        }
        
        log.info("updateList: sumFinal: " + sumTasksForWeek);
        
        averageTasksForWeek.setDowCountSunday(String.valueOf(roundUp(sumTasksForWeek.getDOWCountLongSunday(), sumTasksForWeek.getCounterSunday())));
        averageTasksForWeek.setDowCountMonday(String.valueOf(roundUp(sumTasksForWeek.getDOWCountLongMonday(), sumTasksForWeek.getCounterMonday())));
        averageTasksForWeek.setDowCountTuesday(String.valueOf(roundUp(sumTasksForWeek.getDOWCountLongTuesday(), sumTasksForWeek.getCounterTuesday())));
        averageTasksForWeek.setDowCountWednesday(String.valueOf(roundUp(sumTasksForWeek.getDOWCountLongWednesday(), sumTasksForWeek.getCounterWednesday())));
        averageTasksForWeek.setDowCountThursday(String.valueOf(roundUp(sumTasksForWeek.getDOWCountLongThursday(),sumTasksForWeek.getCounterThursday())));
        averageTasksForWeek.setDowCountFriday(String.valueOf(roundUp(sumTasksForWeek.getDOWCountLongFriday(),sumTasksForWeek.getCounterFriday())));
        averageTasksForWeek.setDowCountSaturday(String.valueOf(roundUp(sumTasksForWeek.getDOWCountLongSaturday(),sumTasksForWeek.getCounterSaturday())));
        averageTasksForWeek.setWeekCount(roundUp(sumTasksForWeek.getWeekCountLong(),sumTasksForWeek.getCounterSaturday())); //use Saturday count as if Sat is complete then so is week

        hRowRecords.getCell(colName).setText(recordTasksForWeek.getWeekName());
        hRowRecords.getCell(colSunday).setText(recordTasksForWeek.getDowCountSunday());
        hRowRecords.getCell(colMonday).setText(recordTasksForWeek.getDowCountMonday());
        hRowRecords.getCell(colTuesday).setText(recordTasksForWeek.getDowCountTuesday());
        hRowRecords.getCell(colWednesday).setText(recordTasksForWeek.getDowCountWednesday());
        hRowRecords.getCell(colThursday).setText(recordTasksForWeek.getDowCountThursday());
        hRowRecords.getCell(colFriday).setText(recordTasksForWeek.getDowCountFriday());
        hRowRecords.getCell(colSaturday).setText(recordTasksForWeek.getDowCountSaturday());
        hRowRecords.getCell(colWeek).setText(recordTasksForWeek.getWeekCount());

        hRowAverages.getCell(colName).setText(averageTasksForWeek.getWeekName());
        hRowAverages.getCell(colSunday).setText(averageTasksForWeek.getDowCountSunday());
        hRowAverages.getCell(colMonday).setText(averageTasksForWeek.getDowCountMonday());
        hRowAverages.getCell(colTuesday).setText(averageTasksForWeek.getDowCountTuesday());
        hRowAverages.getCell(colWednesday).setText(averageTasksForWeek.getDowCountWednesday());
        hRowAverages.getCell(colThursday).setText(averageTasksForWeek.getDowCountThursday());
        hRowAverages.getCell(colFriday).setText(averageTasksForWeek.getDowCountFriday());
        hRowAverages.getCell(colSaturday).setText(averageTasksForWeek.getDowCountSaturday());
        hRowAverages.getCell(colWeek).setText(averageTasksForWeek.getWeekCount());

        Collections.reverse(tasksForWeeks);
        grid.setItems(tasksForWeeks);
        grid.getDataProvider().refreshAll();

        countLabel.setText("(" + sumTasksForWeek.getWeekCount() + " tasks)");

        //get all the month counts
        tasksForMonths.clear();
        Integer monthInProcess = 0;
        for (LocalDate date = defaultStartDate.withDayOfMonth(1); date.isBefore(endDate); date = date.plusMonths(1)) {
            monthInProcess++;
            Long monthCount = taskDetailRepository.findTaskCountByYearMonth(date.getYear(), date.getMonthValue());
            TasksForMonth tasksForMonth = new TasksForMonth();
            tasksForMonth.setStartDate(date);
            tasksForMonth.setMonthCount(monthCount);
            tasksForMonths.add(tasksForMonth);
            if(monthCount> recordTasksForMonth.getMonthCount()){
                recordTasksForMonth.setStartDate(date);
                recordTasksForMonth.setMonthCount(monthCount);
            }
            if(monthInProcess>1){ //skip the first month as it is a part month
                sumTasksForMonth.addToSum(date,monthCount);
            }
            //log.info("updateList: processing month:" + date + " count:" + monthCount);
        }
        //log.info("updateList: record month:" + recordTasksForMonth.getMonthName() + recordTasksForMonth.getMonthCount());
        //create the average record
        averageTasksForMonth.setMonthCount(roundUp(sumTasksForMonth.getMonthCount(),sumTasksForMonth.getMonthCounter()));

        hRowMonthRecord.getCell(colMonthName).setText(recordTasksForMonth.getMonthName());
        hRowMonthRecord.getCell(colMonthCount).setText(recordTasksForMonth.getMonthCount().toString());

        hRowMonthAverage.getCell(colMonthName).setText(averageTasksForMonth.getMonthName());
        hRowMonthAverage.getCell(colMonthCount).setText(averageTasksForMonth.getMonthCount().toString());

        Collections.reverse(tasksForMonths);
        gridMonths.setItems(tasksForMonths);
        gridMonths.getDataProvider().refreshAll();
    }

    private long roundUp(long num, long divisor) {
        return (num + divisor - 1) / divisor;
    }

    private void rebuildAll() {
        //warn user as will delete and rebuild all calculations
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Rebuild task summary data?");
        dialog.setText(
                "Are you sure you want to rebuild and recalculte task summary. This operation may take a few minutes.");

        dialog.setCancelable(true);
        dialog.addCancelListener(event -> UIUtilities.showNotification("Rebuild All skipped"));

        dialog.setConfirmText("Rebuild All");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(event -> rebuildAndUpdate());
        
        dialog.open();
    }

    private void rebuildAndUpdate() {
        UIUtilities.showNotification("Rebuild All complete.");
        //delete all records from TasksForWeek table
        tasksForWeekRepository.deleteAll();
        //then update the list
        updateList();
    }

}
