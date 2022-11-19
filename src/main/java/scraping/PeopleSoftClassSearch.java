package scraping;

import static scraping.PSCoursesParser.*;
import static utils.ArrayJS.*;
import static utils.Nyu.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import me.tongfei.progressbar.ProgressBar;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.slf4j.*;
import utils.*;

public final class PeopleSoftClassSearch {
  static Logger logger =
      LoggerFactory.getLogger("scraping.PeopleSoftClassSearch");

  public static final class SubjectElem {
    public final String schoolName;
    public final String schoolCode;
    public final String code;
    public final String name;
    public final String action;

    SubjectElem(String school, String schoolCode, String code, String name,
                String action) {
      this.schoolName = school;
      this.schoolCode = schoolCode;
      this.code = code;
      this.name = name;
      this.action = action;
    }

    @Override
    public String toString() {
      return "SubjectElem(schoolName=" + schoolName +
          ",schoolCode=" + schoolCode + ",code=" + code + ",name=" + name +
          ",action=" + action + ")";
    }
  }

  public static final class CoursesForTerm
      implements Iterator<ArrayList<Course>> {
    private final ArrayList<SubjectElem> subjects;
    private final ProgressBar bar;
    private final Try ctx;
    private final Term term;

    private PSClient ps;
    private int index = 0;

    private CoursesForTerm(Term term, ProgressBar bar, Try ctx) {
      this.term = term;
      this.bar = bar;
      this.ctx = ctx;

      this.ps = new PSClient();

      if (bar != null) {
        bar.setExtraMessage("fetching subject list...");
        bar.maxHint(-1);
      }

      var resp = ctx.log(() -> {
        ctx.put("term", term);

        return ps.navigateToTerm(term).get();
      });

      this.subjects = ctx.log(() -> parseTermPage(resp.body()));

      if (bar != null) {
        bar.maxHint(subjects.size() + 1);
        bar.step();
      }
    }

    @Override
    public boolean hasNext() {
      return index < subjects.size();
    }

    @Override
    public ArrayList<Course> next() {
      var subject = subjects.get(index);
      index += 1;

      ctx.put("subject", subject);

      if (bar != null)
        bar.setExtraMessage("fetching " + subject.code);

      var parsed = Try.tcPass(() -> {
        for (int i = 0; i < 10; i++) {
          try {
            var resp = ps.fetchSubject(subject).get();
            var body = resp.body();

            return parseSubject(ctx, body, subject.code);
          } catch (Exception e) {
            Thread.sleep(10_000);
            System.out.println(e.getMessage());
            System.out.println(subject);
            ps = new PSClient();
            ps.navigateToTerm(term).get();
          }
        }

        var resp = ps.fetchSubject(subject).get();
        var body = resp.body();

        return parseSubject(ctx, body, subject.code);
      });

      if (bar != null)
        bar.step();

      return parsed;
    }

    public ArrayList<School> getSchools() {
      return ctx.log(() -> translateSubjects(subjects));
    }

    // @Note: This happens to allow JSON serialization of this object to
    // correctly run scraping, by forcing the serialization of the object to
    // run this method, which then consumes the iterator. It's stupid.
    //
    //                                  - Albert Liu, Nov 10, 2022 Thu 22:21
    public ArrayList<Course> getCourses() {
      var courses = new ArrayList<Course>();
      while (this.hasNext()) {
        courses.addAll(this.next());
      }

      return courses;
    }
  }

  public static final class FormEntry {
    public final String key;
    public final String value;

    public FormEntry(String key, String value) {
      this.key = key;
      this.value = value;
    }
  }

  public static ArrayList<School> scrapeSchools(Term term) {
    var ctx = Try.Ctx(logger);

    ctx.put("term", term);

    return ctx.log(() -> {
      var ps = new PSClient();
      var resp = ps.navigateToTerm(term).get();
      var subjects = parseTermPage(resp.body());
      return translateSubjects(subjects);
    });
  }

  public static ArrayList<Course> scrapeSubject(Term term, String subjectCode) {
    var ctx = Try.Ctx(logger);

    ctx.put("term", term);
    ctx.put("subject", subjectCode);

    return ctx.log(() -> {
      var ps = new PSClient();
      var resp = ps.navigateToTerm(term).get();
      var subjects = parseTermPage(resp.body());

      var subject = find(subjects, s -> s.code.equals(subjectCode));
      if (subject == null)
        throw new RuntimeException("Subject not found: " + subjectCode);

      {
        resp = ps.fetchSubject(subject).get();
        var responseBody = resp.body();

        return parseSubject(ctx, responseBody, subject.code);
      }
    });
  }

  /**
   * @param term The term to scrape
   * @param bar Nullable progress bar to output progress to
   */
  public static CoursesForTerm scrapeTerm(Term term, ProgressBar bar) {
    var ctx = Try.Ctx(logger);
    return new CoursesForTerm(term, bar, ctx);
  }

  static ArrayList<SubjectElem> parseTermPage(String responseBody) {
    var doc = Jsoup.parse(responseBody, PSClient.MAIN_URL);

    var field = doc.expectFirst("#win0divNYU_CLASS_SEARCH");
    var cdata = (CDataNode)field.textNodes().get(0);

    doc = Jsoup.parse(cdata.text(), PSClient.MAIN_URL);
    var results = doc.expectFirst("#win0divRESULTS");
    var group = results.expectFirst("div[id=win0divGROUP$0]");

    var out = new ArrayList<SubjectElem>();
    for (var child : group.children()) {
      var schoolH2 = child.expectFirst("h2");
      var school = schoolH2.text();

      var schoolTags = child.select("div.ps_box-link");
      for (var schoolTag : schoolTags) {
        var schoolTitle = schoolTag.text();
        var parts = schoolTitle.split("\\(");

        var titlePart = parts[0].trim();
        var codePart = parts[1];
        codePart = codePart.substring(0, codePart.length() - 1);

        var schoolCode = codePart.split("_")[0].split("-")[1];

        var action = schoolTag.id().substring(7);

        out.add(
            new SubjectElem(school, schoolCode, codePart, titlePart, action));
      }
    }

    return out;
  }
}
