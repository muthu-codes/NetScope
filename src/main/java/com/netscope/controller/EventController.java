package com.netscope.controller;

import com.netscope.model.NetworkEvent;
import com.netscope.service.EventService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService events;

    public EventController(EventService events) {
        this.events = events;
    }

    @GetMapping
    public List<NetworkEvent> list(@RequestParam(defaultValue = "100") int limit,
                                   @RequestParam(required = false) String type,
                                   @RequestParam(required = false) String severity) {
        return events.latest(limit, type, severity);
    }
}
