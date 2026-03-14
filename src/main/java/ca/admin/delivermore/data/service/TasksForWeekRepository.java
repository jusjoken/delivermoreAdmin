/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package ca.admin.delivermore.data.service;

import ca.admin.delivermore.data.report.TasksForWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 *
 * @author birch
 */
public interface TasksForWeekRepository extends JpaRepository<TasksForWeek, LocalDate> {
    @Override
    List<TasksForWeek> findAll();

    //retrieve the newest week record
    TasksForWeek findFirstByOrderByStartDateDesc();

}
