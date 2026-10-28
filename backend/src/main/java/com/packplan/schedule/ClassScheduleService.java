package com.packplan.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ClassScheduleService {
    private static final String SEARCH_URL = "https://webappprd.acs.ncsu.edu/php/coursecat/search.php";
    private static final Pattern COURSE_CODE = Pattern.compile("(?i)^([A-Z]{1,4})\\s*(\\d{3})([A-Z]?)$");
    private static final Pattern TIME_RANGE = Pattern.compile("(?i)(\\d{1,2}:\\d{2}\\s*[AP]M)\\s*[-–]\\s*(\\d{1,2}:\\d{2}\\s*[AP]M)");
    private final ObjectMapper objectMapper;

    public ClassScheduleService(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    public ScheduleResponse findSections(String rawCourseCode, String term) {
        var code = rawCourseCode.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        var matcher = COURSE_CODE.matcher(code);
        if (!matcher.matches()) throw new IllegalArgumentException("Invalid course code");
        var subject = matcher.group(1);
        var number = matcher.group(2) + matcher.group(3);
        String payload = "term=" + enc(term) + "&subject=" + enc(subject) + "&course-number=" + enc(number)
                + "&course-inequality=%3D&course-career=UGRD&current_strm=" + enc(term);
        try {
            var response = Jsoup.connect(SEARCH_URL).timeout(8000).ignoreContentType(true)
                    .header("Accept", "application/json, text/javascript, */*; q=0.01")
                    .header("X-Requested-With", "XMLHttpRequest").requestBody(payload)
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .method(Connection.Method.POST).execute();
            var root = objectMapper.readTree(response.body());
            String html = root.path("html").asText("");
            if (root.has("error") || html.isBlank()) return new ScheduleResponse(term, code, null, null, null, List.of(), Instant.now().toString(), searchUrl(term, subject, number));
            return parse(html, term, code, searchUrl(term, subject, number));
        } catch (IOException exception) {
            throw new IllegalStateException("Class search request failed", exception);
        }
    }

    static ScheduleResponse parse(String html, String term, String code, String searchUrl) {
        Document document = Jsoup.parseBodyFragment(html);
        Element course = document.selectFirst("section.course");
        if (course == null) return new ScheduleResponse(term, code, null, null, null, List.of(), Instant.now().toString(), searchUrl);
        Element heading = course.selectFirst("h1");
        String title = heading == null ? null : heading.select("small").text();
        String credits = heading == null ? null : heading.select(".units").text().replaceFirst("(?i)^Units:\\s*", "").trim();
        var description = course.select("p").stream().findFirst().map(Element::text).orElse("");
        List<Section> sections = new ArrayList<>();
        for (Element row : course.select("table.section-table tbody tr")) {
            var cells = row.select("td");
            if (cells.size() < 8) continue;
            String rawMeetingText = cells.get(4).text().replaceAll("\\s+", " ").trim();
            Matcher time = TIME_RANGE.matcher(rawMeetingText);
            String start = null, end = null;
            String timeText = "";
            if (time.find()) { start = to24Hour(time.group(1)); end = to24Hour(time.group(2)); timeText = time.group(1) + " - " + time.group(2); }
            List<String> days = cells.get(4).select("li.meet abbr").eachAttr("title").stream()
                    .map(value -> value.replaceFirst("\\s*-\\s*meet$", "").trim()).filter(value -> !value.isBlank()).toList();
            String meetingText = !days.isEmpty() ? String.join(", ", days) + (timeText.isBlank() ? "" : " · " + timeText) : rawMeetingText;
            Element notesCell = cells.size() > 9 ? cells.get(9) : cells.get(cells.size() - 1);
            List<String> notes = notesCell.select("[data-content]").eachAttr("data-content").stream()
                    .map(value -> Jsoup.parse(value).text()).filter(value -> !value.isBlank()).toList();
            String availability = cells.get(3).text().replaceAll("\\s+", " ").trim();
            String classNumber = cells.get(2).text().trim();
            sections.add(new Section(cells.get(0).text().trim(), cells.get(1).text().trim(), classNumber,
                    availability, days, start, end, meetingText, cells.get(5).text().trim(),
                    cells.get(6).text().trim(), cells.get(7).text().trim(), notes));
        }
        return new ScheduleResponse(term, code, title, credits, description, sections, Instant.now().toString(), searchUrl);
    }

    private static String to24Hour(String value) {
        var matcher = Pattern.compile("(?i)^(\\d{1,2}):(\\d{2})\\s*([AP])M$").matcher(value.trim());
        if (!matcher.matches()) return null;
        int hour = Integer.parseInt(matcher.group(1)) % 12;
        if (matcher.group(3).equalsIgnoreCase("P")) hour += 12;
        return "%02d:%s".formatted(hour, matcher.group(2));
    }
    private static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String searchUrl(String term, String subject, String number) {
        return "https://webappprd.acs.ncsu.edu/php/coursecat/index.php?term=" + enc(term) + "&subject=" + enc(subject) + "&course-number=" + enc(number);
    }

    public record ScheduleResponse(String term, String courseCode, String title, String credits,
                                   String description, List<Section> sections, String retrievedAt, String sourceUrl) {}
    public record Section(String section, String component, String classNumber, String availability,
                          List<String> meetingDays, String startTime, String endTime, String meetingText,
                          String location, String instructor, String dates, List<String> notes) {}
}
