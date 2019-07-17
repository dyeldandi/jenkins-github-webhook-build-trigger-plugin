/*
 * Licensed under MIT License
 * Copyright (c) 2017 Bernhard Grünewaldt
 */
package io.codeclou.jenkins.githubwebhookbuildtriggerplugin;

/**
 * GitHub Webhook JSON Pojo with only the parts that are interesting for us.
 * See: https://developer.github.com/webhooks/#payloads
 */
public class GithubWebhookPayload {

    /*
     * hook_id is only set on initial request when the webhook is created.
     * See: https://developer.github.com/webhooks/#ping-event
     */
    private Long hook_id;
    private String type;
    private String ref;
    private String ref_type;
    private String before;
    private String after;
    private ArrayList<GithubWebhookPayloadCommit> commits;
    private GithubWebhookPayloadCommit head_commit;
    private GithubWebhookPayloadRepository repository;

    private ArrayList<GithubWebhookPayloadJenkinsFlag> jFlags;
    private static final Pattern flagPattern;
    private Matcher flagMatcher;

    static {
        flagPattern = Pattern.compile("\\[jenkins:([a-zA-Z0-9_-]+)(?:=([a-zA-Z0-9/\\.,_-]+))?\\]");
        
    }

    public GithubWebhookPayload() {

    }

    public void findFlags() {
        for (GithubWebhookPayloadCommit commit : commits) {
            flagMatcher = flagPattern.matcher(commit.message);
            while(flagMatcher.find()) {
                if (flagMatcher.groupCount() == 2) {
                    jFlags.append(GithubWebhookPayloadJenkinsFlag(flagMatcher.group(1), flagMatcher.group(2)));
                } else if (flagMatcher.groupCount() == 1) {
                    jFlags.append(GithubWebhookPayloadJenkinsFlag(flagMatcher.group(1)));
                }
            }
        }
    }

    public ArrayList<GithubWebhookPayloadJenkinsFlag> getJFlags() {
        return jFlags
    }

    public boolean hasJFlags() {
        return jFlags.size() > 0;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    public String getRef_type() {
        return ref_type;
    }

    public void setRef_type(String ref_type) {
        this.ref_type = ref_type;
    }

    public String getBefore() {
        return before;
    }

    public void setBefore(String before) {
        this.before = before;
    }

    public String getAfter() {
        return after;
    }

    public void setAfter(String after) {
        this.after = after;
    }

    public GithubWebhookPayloadRepository getRepository() {
        return repository;
    }

    public void setRepository(GithubWebhookPayloadRepository repository) {
        this.repository = repository;
    }

    public Long getHook_id() {
        return hook_id;
    }

    public void setHook_id(Long hook_id) {
        this.hook_id = hook_id;
    }

    public class GithubWebhookPayloadRepository {
        private String clone_url;
        private String html_url;
        private String name;
        private String full_name;

        public GithubWebhookPayloadRepository() {

        }

        public String getClone_url() {
            return clone_url;
        }

        public void setClone_url(String clone_url) {
            this.clone_url = clone_url;
        }

        public String getHtml_url() {
            return html_url;
        }

        public void setHtml_url(String html_url) {
            this.html_url = html_url;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getFull_name() {
            return full_name;
        }

        public void setFull_name(String full_name) {
            this.full_name = full_name;
        }
    }

    public class GithubWebhookPayloadCommit {
        private String id;
        private String tree_id;
        private String message;
        private String timestamp;
        private String url;

        public GithubWebhookPayloadCommit() {

        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getTree_id() {
            return tree_id;
        }

        public void setTree_id(String tree_id) {
            this.tree_id = tree_id;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    public class GithubWebhookPayloadJenkinsFlag {
        private String name;
        private String value;

        public GithubWebhookPayloadJenkinsFlag(String name) {
            this.name = name;
            this.value = "";
        }

        public GithubWebhookPayloadJenkinsFlag(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }

        public boolean hasValue() {
            return ! value.isEmpty();
        }
}
