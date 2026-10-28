package com.packplan.schedule;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClassScheduleServiceTest {
    @Test
    void parsesSpringSectionsMeetingTimesAvailabilityAndNotes() {
        String html = """
                <section class="course"><h1>CSC 116 <small>Introduction to Computing - Java</small>
                  <span class="units pull-right">Units: 3</span></h1>
                  <p>Introductory course description.</p><table class="section-table"><tbody><tr>
                  <td>002</td><td>Lec</td><td>1632</td><td><span>Open</span><br>12/40</td>
                  <td><ul class="weekdisplay"><li class="meet"><abbr title="Monday - meet">M</abbr></li>
                  <li class="meet"><abbr title="Wednesday - meet">W</abbr></li></ul> 10:40 AM - 12:30 PM</td>
                  <td>200 Lampe Drive</td><td>Jiao, Shuyin</td><td>01/11/27 - 04/27/27</td><td></td>
                  <td><a data-content="Check course notes."></a></td></tr></tbody></table></section>
                """;

        var result = ClassScheduleService.parse(html, "2271", "CSC 116", "https://example.edu/search");

        assertThat(result.courseCode()).isEqualTo("CSC 116");
        assertThat(result.title()).isEqualTo("Introduction to Computing - Java");
        assertThat(result.credits()).isEqualTo("3");
        assertThat(result.sections()).hasSize(1);
        var section = result.sections().getFirst();
        assertThat(section.section()).isEqualTo("002");
        assertThat(section.component()).isEqualTo("Lec");
        assertThat(section.classNumber()).isEqualTo("1632");
        assertThat(section.availability()).isEqualTo("Open 12/40");
        assertThat(section.meetingDays()).containsExactly("Monday", "Wednesday");
        assertThat(section.startTime()).isEqualTo("10:40");
        assertThat(section.endTime()).isEqualTo("12:30");
        assertThat(section.meetingText()).isEqualTo("Monday, Wednesday · 10:40 AM - 12:30 PM");
        assertThat(section.location()).isEqualTo("200 Lampe Drive");
        assertThat(section.instructor()).isEqualTo("Jiao, Shuyin");
        assertThat(section.dates()).isEqualTo("01/11/27 - 04/27/27");
        assertThat(section.notes()).containsExactly("Check course notes.");
    }

    @Test
    void handlesNoSectionsAndAsyncMeetingsWithoutInventingMeetingTimes() {
        var empty = ClassScheduleService.parse("<div>No results</div>", "2271", "CSC 999", "https://example.edu");
        assertThat(empty.sections()).isEmpty();

        String html = "<section class='course'><h1>CSC 999 <small>Independent Study</small></h1>"
                + "<table class='section-table'><tr><td>001</td><td>Ind</td><td>9001</td><td>Open 4/20</td>"
                + "<td>Online - Asynchronous</td><td>Online</td><td></td><td>01/11/27 - 04/27/27</td></tr></table></section>";
        var asyncSection = ClassScheduleService.parse(html, "2271", "CSC 999", "https://example.edu").sections().getFirst();
        assertThat(asyncSection.meetingDays()).isEmpty();
        assertThat(asyncSection.startTime()).isNull();
        assertThat(asyncSection.endTime()).isNull();
    }
}
