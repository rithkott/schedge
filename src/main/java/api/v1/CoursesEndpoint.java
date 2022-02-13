package api.v1;

import static utils.TryCatch.*;

import api.*;
import database.GetConnection;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.dsl.OpenApiDocumentation;
import java.util.*;
import types.*;
import utils.*;

public final class CoursesEndpoint extends App.Endpoint {

  public String getPath() { return "/{term}/courses/{subject}"; }

  public OpenApiDocumentation configureDocs(OpenApiDocumentation docs) {
    return docs
        .operation(openApiOperation -> {
          openApiOperation.description(
              "This endpoint returns a list of courses for a specific semester and subject.");
          openApiOperation.summary("Courses Endpoint");
        })
        .pathParam(
            "term", String.class,
            openApiParam -> {
              openApiParam.description(
                  "Must be a valid term code, either 'current', 'next', or something "
                  + "like sp2021 for Spring 2021. Use 'su' for Summer, 'sp' "
                  + "for Spring, 'fa' for Fall, and 'ja' for January/JTerm");
            })
        .pathParam(
            "subject", String.class,
            openApiParam -> {
              openApiParam.description(
                  "Must be a valid subject code. Take a look at the docs for the schools endpoint for more information.");
            })
        .queryParam(
            "full", Boolean.class, false,
            openApiParam -> {
              openApiParam.description(
                  "Whether to return campus, description, grading, nodes, "
                  + "and prerequisites.");
            })
        .jsonArray("200", Course.class,
                   openApiParam -> { openApiParam.description("OK."); })
        .json("400", App.ApiError.class, openApiParam -> {
          openApiParam.description(
              "One of the values in the path parameter was not valid.");
        });
  }

  public Handler getHandler() {
    return ctx -> {
      TryCatch tc = tcNew(e -> {
        ctx.status(400);
        ctx.json(new App.ApiError(e.getMessage()));
      });

      Term term = tc.log(() -> {
        String termString = ctx.pathParam("term");
        if (termString.contentEquals("current")) {
          return Term.getCurrentTerm();
        }

        if (termString.contentEquals("next")) {
          return Term.getCurrentTerm().nextTerm();
        }

        int year = Integer.parseInt(termString.substring(2));
        return new Term(termString.substring(0, 2), year);
      });

      if (term == null)
        return;

      Subject subject = tc.log(() -> {
        String subjectString = ctx.pathParam("subject").toUpperCase();
        return Subject.fromCode(subjectString);
      });

      if (subject == null)
        return;

      String fullData = ctx.queryParam("full");

      Object output = GetConnection.withConnectionReturning(conn -> {
        List<Subject> subjects = Collections.singletonList(subject);

        if (fullData != null && fullData.toLowerCase().contentEquals("true")) {
          return SelectCourses.selectFullCourses(conn, term, subjects);
        }

        return SelectCourses.selectCourses(conn, term, subjects);
      });

      ctx.status(200);
      ctx.json(output);
    };
  }
}
