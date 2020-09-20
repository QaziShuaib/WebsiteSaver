package com.curtisnewbie.web;

import com.curtisnewbie.api.HtmlUtil;
import com.curtisnewbie.api.PdfUtil;
import com.curtisnewbie.api.TaskHandler;
import com.curtisnewbie.impl.HtmlContentIncorrectException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author zhuangyongj
 */
@RestController
@RequestMapping("/download")
public class HtmlDownloadController {

    private static final Logger logger = LoggerFactory.getLogger(HtmlDownloadController.class);

    @Autowired
    private HtmlUtil htmlUtil;

    @Autowired
    private PdfUtil pdfUtil;

    @Autowired
    private TaskHandler taskHandler;

    @Value("${rootDir}")
    private String rootDir;

    @GetMapping
    public ResponseEntity fetchAndConvert2Pdf(@RequestHeader("url") String url,
                                              @RequestHeader("path") String path) {
        taskHandler.asyncHandle(() -> {
            fetchAndConvert2pdf(url, path);
        });
        return ResponseEntity.ok().build();
    }

    @PostMapping("/with/chrome")
    public ResponseEntity convertWithChrome(String url, String path) {
        taskHandler.asyncHandle(() -> {
            grabWithChrome(url, path);
        });
        return ResponseEntity.ok().build();
    }

    // TODO: Fix temporary code
    /**
     * Grab webpage with the support of chrome (headless)
     *
     * @param url    of web page
     * @param path filename / path
     */
    private void grabWithChrome(String url, String path) {
        // remove all spaces and append .pdf if necessary
        path = appendPdfFileExt(removeSpaces(path));
        logger.info(">>> [BEGIN] Request fetching and converting webpage '{}' to pdf '{}' using chrome " + "(headless) ", Thread.currentThread().getId(), url, path);
        try {
            // FIXME: won't work for some reasons if we do it like '... --print-to-pdf="%s%s" "%s"', quotes are not handled properly
            String cmd = String.format("google-chrome --headless --print-to-pdf=%s%s %s", rootDir, path, url);
            logger.info(">>> Created command \"{}\" for chrome (headless)", cmd);
            Runtime runtime = Runtime.getRuntime();
            Process p = runtime.exec(cmd);
            // getInputStream() has no input, use getErrorStream() instead
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
                String line;
                while (p.isAlive() && (line = br.readLine()) != null) {
                    logger.info(">>> [PROCESS] {}", line);
                }
            }
            logger.info(">>> [END] Finish fetching and converting webpage {} to pdf {}", url, path);
        } catch (IOException e) {
            logger.error(">>> [ERROR] Failed to fetch html content. Error Msg: {}", e);
        }
    }

    /**
     * Grab pure html content and convert to pdf programmatically with Itext library
     *
     * @param url
     * @param target
     */
    private void fetchAndConvert2pdf(String url, String target) {
        logger.info(">>> [BEGIN] Request fetching and converting webpage '{}' to pdf '{}'", url, target);
        try {
            String ctn = htmlUtil.grabHtmlContent(url);
            String baseUrl = htmlUtil.extractBaseUrl(ctn); // check if a base url is declared
            if (baseUrl == null)
                baseUrl = url;
            if (!pdfUtil.toPdfFile(ctn, baseUrl, target)) {
                logger.error(">>> [ERROR] Failed to convert to pdf");
            }
        } catch (IOException | HtmlContentIncorrectException e) {
            logger.error(">>> [ERROR] Failed to fetch html content. Error Msg: {}", e);
        }
        logger.info(">>> [END] Finish fetching and converting webpage {} to pdf {}", url, target);
    }

    private String removeSpaces(String str) {
        return str.replaceAll("\\s", "");
    }

    private String appendPdfFileExt(String path) {
        String mPath = path;
        if (!mPath.matches("^.*\\.[Pp][Dd][Ff]$")) {
            if (mPath.endsWith("."))
                mPath += "pdf";
            else
                mPath += ".pdf";
        }
        return mPath;
    }
}
