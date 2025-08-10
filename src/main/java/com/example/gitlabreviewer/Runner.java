package com.example.gitlabreviewer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class Runner implements CommandLineRunner {

    private final GitlabService gitlabService;
    private final GroqClient groqClient;

    @Value("${gitlab.host:}")
    private String gitlabHost;

    @Value("${gitlab.repoPath:}")
    private String repoPath;

    @Value("${gitlab.mrIid:}")
    private String mrIid;

    @Value("${gitlab.token:}")
    private String gitlabToken;

    @Value("${groq.api.url:https://api.groq.com/openai/v1/chat/completions}")
    private String groqApiUrl;

    @Value("${groq.api.key:}")
    private String groqApiKey;

    @Value("${groq.model:meta-llama/llama-4-scout-17b-16e-instruct}")
    private String groqModel;

    public Runner(GitlabService gitlabService, GroqClient groqClient) {
        this.gitlabService = gitlabService;
        this.groqClient = groqClient;
    }

    @Override
    public void run(String... args) throws Exception {

        // priority: system props/args > application.properties
        if (args != null && args.length >= 4) {
            gitlabHost = args[0];
            repoPath = args[1];
            mrIid = args[2];
            gitlabToken = args[3];
            if (args.length >= 5) groqApiKey = args[4];
        }

        if (gitlabHost == null || gitlabHost.isBlank() ||
            repoPath == null || repoPath.isBlank() ||
            mrIid == null || mrIid.isBlank()) {
            System.out.println("Usage: provide gitlab.host, gitlab.repoPath, gitlab.mrIid and gitlab.token either via application.properties or command-line args.");
            System.out.println("Example:");
            System.out.println("mvn spring-boot:run -Dgitlab.host=http://blrgitlab.comviva.com -Dgitlab.repoPath=dfs-core/orchestration/soe -Dgitlab.mrIid=42 -Dgitlab.token=glpat_xxx -Dgroq.api.key=gsk_xxx");
            return;
        }

        System.out.println("Fetching project id for repo: " + repoPath);
        int projectId = gitlabService.fetchProjectId(gitlabHost, repoPath, gitlabToken);
        System.out.println("Project ID: " + projectId);

        System.out.println("Fetching MR diffs for iid: " + mrIid);
        String diff = gitlabService.fetchMrDiff(gitlabHost, projectId, mrIid, gitlabToken);
        System.out.println("---- Diff extracted (truncated to 2000 chars) ----\n" + (diff.length()>2000?diff.substring(0,2000)+"\n...[truncated]":"" ) );

        String prompt = "You are a senior Java engineer and code reviewer. Review the following git diff and provide: 1) high-level summary, 2) bugs, 3) security concerns, 4) performance concerns, 5) style suggestions.\n\nDiff:\n" + diff;

        System.out.println("Sending diff to Groq for review (model: " + groqModel + ")...");
        String feedback = groqClient.reviewWithGroq(groqApiUrl, groqApiKey, groqModel, prompt);
        System.out.println("===== AI Code Review Feedback =====\n" + feedback);
    }
}
