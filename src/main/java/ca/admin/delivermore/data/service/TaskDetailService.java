package ca.admin.delivermore.data.service;

import ca.admin.delivermore.data.entity.TaskEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TaskDetailService {
    private TaskDetailRepository taskDetailRepository;

    public TaskDetailService(TaskDetailRepository taskDetailRepository){

        this.taskDetailRepository = taskDetailRepository;
    }

    public List<TaskEntity> findAllTaskDetails(LocalDateTime fromDate, LocalDateTime toDate) {
        System.out.println("findAllTaskDetails: from:" + fromDate + " to:" + toDate);
        if (fromDate == null || toDate == null) {
            System.out.println("findAllTaskDetails: running search with default dates");
            return taskDetailRepository.search(LocalDateTime.parse("2022-08-14T00:00:00"), LocalDateTime.parse("2022-08-14T23:59:59"));
        } else {
            System.out.println("findAllTaskDetails: running search using passed in dates");
            return taskDetailRepository.search(fromDate, toDate);
        }
    }

    public long countTaskDetail() {
        return taskDetailRepository.count();
    }

    public void deleteTaskDetail(TaskEntity taskEntity) {
        taskDetailRepository.delete(taskEntity);
    }

    public void saveTaskDetail(TaskEntity taskEntity) {
        if (taskEntity == null) {
            System.err.println("TaskDetail is null. Are you sure you have connected your form to the application?");
            return;
        }
        taskDetailRepository.save(taskEntity);
    }

}