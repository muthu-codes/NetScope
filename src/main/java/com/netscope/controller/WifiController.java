package com.netscope.controller;

import com.netscope.model.WifiSurvey;
import com.netscope.service.WifiSurveyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wifi")
public class WifiController {

    private final WifiSurveyService wifi;

    public WifiController(WifiSurveyService wifi) {
        this.wifi = wifi;
    }

    /** Passive survey of the Wi-Fi access points this computer can hear (takes 1-2 seconds). */
    @GetMapping
    public WifiSurvey survey() {
        return wifi.survey();
    }
}
