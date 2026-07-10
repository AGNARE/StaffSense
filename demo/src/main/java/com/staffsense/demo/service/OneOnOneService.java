package com.staffsense.demo.service;

import com.staffsense.demo.model.*;
import com.staffsense.demo.repository.OneOnOneMeetingRepository;
import com.staffsense.demo.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OneOnOneService {

    private final OneOnOneMeetingRepository meetingRepository;
    private final UserRepository userRepository;

    public OneOnOneService(OneOnOneMeetingRepository meetingRepository, UserRepository userRepository) {
        this.meetingRepository = meetingRepository;
        this.userRepository = userRepository;
    }

    public List<OneOnOneMeeting> findAllForManager(User manager) {
        return meetingRepository.findAll().stream()
                .filter(m -> m.getManager().getId().equals(manager.getId()))
                .sorted(java.util.Comparator.comparing(OneOnOneMeeting::getScheduledAt).reversed())
                .toList();
    }

    public List<OneOnOneMeeting> findAllForEmployee(User employee) {
        return meetingRepository.findAll().stream()
                .filter(m -> m.getEmployee().getId().equals(employee.getId()))
                .sorted(java.util.Comparator.comparing(OneOnOneMeeting::getScheduledAt).reversed())
                .toList();
    }

    public List<OneOnOneMeeting> findAll() {
        return meetingRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(OneOnOneMeeting::getScheduledAt).reversed())
                .toList();
    }

    public Page<OneOnOneMeeting> findAllForEmployee(User employee, Pageable pageable) {
        return meetingRepository.findByEmployeeId(employee.getId(), pageable);
    }

    public Page<OneOnOneMeeting> findAllForManager(User manager, Pageable pageable) {
        return meetingRepository.findByManagerId(manager.getId(), pageable);
    }

    public Page<OneOnOneMeeting> findAll(Pageable pageable) {
        return meetingRepository.findAll(pageable);
    }

    public OneOnOneMeeting findById(Long id) {
        return meetingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Meeting not found: " + id));
    }

    public OneOnOneMeeting create(Long employeeId, Long managerId, LocalDateTime scheduledAt) {
        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found"));
        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> new IllegalArgumentException("Manager not found"));
        OneOnOneMeeting meeting = new OneOnOneMeeting();
        meeting.setEmployee(employee);
        meeting.setManager(manager);
        meeting.setScheduledAt(scheduledAt);
        meeting.setStatus(MeetingStatus.PLANNED);
        meeting.setMissed(false);
        return meetingRepository.save(meeting);
    }

    public OneOnOneMeeting complete(Long id, String notes, String actionItems) {
        OneOnOneMeeting m = findById(id);
        m.setStatus(MeetingStatus.COMPLETED);
        m.setNotes(notes);
        m.setActionItems(actionItems);
        return meetingRepository.save(m);
    }

    public OneOnOneMeeting cancel(Long id) {
        OneOnOneMeeting m = findById(id);
        m.setStatus(MeetingStatus.CANCELLED);
        return meetingRepository.save(m);
    }
}
