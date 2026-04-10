package no.rutebanken.marduk.domain;

import java.util.List;

public class CronInfo {
    private Long date;
    private Integer hours;
    private Integer minutes;
    private List<Integer> applicationDays;

    public Long getDate() {
        return date;
    }

    public void setDate(Long date) {
        this.date = date;
    }

    public Integer getHours() {
        return hours;
    }

    public void setHours(Integer hours) {
        this.hours = hours;
    }

    public Integer getMinutes() {
        return minutes;
    }

    public void setMinutes(Integer minutes) {
        this.minutes = minutes;
    }

    public List<Integer> getApplicationDays() {
        return applicationDays;
    }

    public void setApplicationDays(List<Integer> applicationDays) {
        this.applicationDays = applicationDays;
    }
}
