package edu.whut.eval.interfaces.admin;

import edu.whut.eval.application.finalrecord.exporting.FinalScoreExportApplicationService;
import edu.whut.eval.application.finalrecord.exporting.FinalScoreExportFile;
import edu.whut.eval.application.finalrecord.exporting.FinalScoreExportQuery;
import edu.whut.eval.common.exception.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/exports")
public class AdminFinalScoreExportController {

    private final FinalScoreExportApplicationService exportApplicationService;

    public AdminFinalScoreExportController(FinalScoreExportApplicationService exportApplicationService) {
        this.exportApplicationService = exportApplicationService;
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).SCORE_EXPORT_ASSIGNED)")
    @GetMapping("/final-scores")
    public ResponseEntity<byte[]> exportFinalScores(HttpServletRequest request) {
        Map<String, String[]> parameters = request.getParameterMap();
        rejectPaginationParameters(parameters);
        rejectRepeatedSingleValueParameters(parameters);
        FinalScoreExportQuery query = new FinalScoreExportQuery(
                singleValue(parameters, "academicYear"),
                singleValue(parameters, "status"),
                singleValue(parameters, "grade"),
                rawClasses(request)
        );
        FinalScoreExportFile file = exportApplicationService.export(query);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }

    private void rejectPaginationParameters(Map<String, String[]> parameters) {
        if (parameters.containsKey("pageNo") || parameters.containsKey("pageSize")) {
            throw new ValidationException("导出接口不支持分页参数");
        }
    }

    private void rejectRepeatedSingleValueParameters(Map<String, String[]> parameters) {
        for (String name : List.of("academicYear", "status", "grade")) {
            String[] values = parameters.get(name);
            if (values != null && values.length > 1) {
                throw new ValidationException("导出接口不支持重复单值参数");
            }
        }
    }

    private String singleValue(Map<String, String[]> parameters, String name) {
        String[] values = parameters.get(name);
        return values == null || values.length == 0 ? null : values[0];
    }

    private List<String> rawClasses(HttpServletRequest request) {
        String[] values = request.getParameterValues("classes");
        return values == null ? null : Arrays.asList(values);
    }
}
