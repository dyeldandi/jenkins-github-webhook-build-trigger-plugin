/*
 * Licensed under MIT License
 * Copyright (c) 2017 Bernhard Grünewaldt
 * Copyright (c) 2019 Denis Yeldandi
 */
package io.codeclou.jenkins.githubwebhookbuildtriggerplugin;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import java.lang.reflect.Type;
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
    private String before = "";
    private String after = "";
    private ArrayList<GithubWebhookPayloadCommit> commits;
    private GithubWebhookPayloadCommit head_commit;
    private GithubWebhookPayloadRepository repository;
    private GithubWebhookPayloadSender sender;
    private GithubWebhookPayloadPerson pusher;

    private String releaseVer;
    private boolean releaseTag;
    private ArrayList<GithubWebhookPayloadJenkinsFlag> jFlags;
    private ArrayList<GithubWebhookPayloadJenkinsCommitFlags> jcFlags;
    private static final Pattern flagPattern;
    private Matcher flagMatcher;

    static {
        flagPattern = Pattern.compile("\\[jenkins:([a-zA-Z0-9_-]+)(?:=([a-zA-Z0-9/\\.,_-]+))?\\]");
        
    }

    public GithubWebhookPayload() {
	jFlags = new ArrayList<GithubWebhookPayloadJenkinsFlag>();
	jcFlags = new ArrayList<GithubWebhookPayloadJenkinsCommitFlags>();
    }

    public void findFlags() {
	if (commits != null) {
            DateTime timeNow = new DateTime();
            for (GithubWebhookPayloadCommit commit : commits) {
                if (timeNow.getMillis() - commit.timestamp.getMillis() < 600*1000) {
                    GithubWebhookPayloadJenkinsCommitFlags jcFlag = new GithubWebhookPayloadJenkinsCommitFlags(commit.getId(), commit.getCommitter());
                    ArrayList<GithubWebhookPayloadJenkinsFlag> jcFlagArray = jcFlag.getJFlags();
                    flagMatcher = flagPattern.matcher(commit.message);
                    while(flagMatcher.find()) {
                        GithubWebhookPayloadJenkinsFlag newflag = null;
                        if (flagMatcher.groupCount() == 2) {
                            newflag = new GithubWebhookPayloadJenkinsFlag(flagMatcher.group(1), flagMatcher.group(2));
                        } else if (flagMatcher.groupCount() == 1) {
                            newflag = new GithubWebhookPayloadJenkinsFlag(flagMatcher.group(1));
                        }
                        if (!jFlags.contains(newflag)) {
                            jFlags.add(newflag);
                        }
                        if (!jcFlagArray.contains(newflag)) {
                            jcFlagArray.add(newflag);
                        }
                    }
                    if (!jcFlagArray.isEmpty()) {
                        jcFlags.add(jcFlag);
                    }
                }
            }
        }
    }

    public ArrayList<GithubWebhookPayloadJenkinsFlag> getJFlags() {
        return jFlags;
    }

    public ArrayList<GithubWebhookPayloadJenkinsCommitFlags> getJCFlags() {
        return jcFlags;
    }


    public void findRelease() {
        if (ref.startsWith("release/")) {
            releaseTag = true;
            releaseVer = ref.substring(8);
	} else {
            releaseTag = false;
	}
    }

    public boolean hasJFlags() {
        return jFlags.size() > 0;
    }

    public boolean hasJCFlags() {
        return jcFlags.size() > 0;
    }

    public boolean isReleaseTag() {
        return releaseTag;
    }

    public String getRelease() {
        return releaseVer;
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

    public GithubWebhookPayloadSender getSender() {
        return sender;
    }

    public void setSender(GithubWebhookPayloadSender sender) {
        this.sender = sender;
    }

    public GithubWebhookPayloadPerson getPusher() {
        return pusher;
    }

    public void setPusher(GithubWebhookPayloadPerson pusher) {
        this.pusher = pusher;
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

    public class GithubWebhookPayloadSender {
        private String login;
        private int id;
        private String avatar_url;
        private String type;

        public GithubWebhookPayloadSender() {
        }

        public String getLogin() {
            return login;
        }

        public void setLogin(String login) {
            this.login = login;
        }

        public int getId() {
            return id;
        }

	public void setId(int id) {
            this.id = id;
        }

        public String getAvatar_url() {
            return avatar_url;
        }

        public void setAvatar_url(String avatar_url) {
            this.avatar_url = avatar_url;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    public class GithubWebhookPayloadCommit {
        private String id;
        private String tree_id;
        private String message;
        private DateTime timestamp;
        private String url;
        private GithubWebhookPayloadPerson author;
        private GithubWebhookPayloadPerson committer;

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

        public DateTime getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(DateTime timestamp) {
            this.timestamp = timestamp;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public GithubWebhookPayloadPerson getAuthor() {
            return author;
        }

        public GithubWebhookPayloadPerson getCommitter() {
            return committer;
        }
    }

    public class GithubWebhookPayloadJenkinsFlag {
        private String name;
        private String value;
	private String ref;

        public GithubWebhookPayloadJenkinsFlag(String name) {
            this.name = name;
            this.value = "";
            this.ref = "";
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
            return value != null && ! value.isEmpty();
        }

	public boolean equals(GithubWebhookPayloadJenkinsFlag other) {
            if (other == null) return false;
	    return other.name.equals(this.name) && other.value.equals(this.value);
	}

	public int hashCode() {
	    return this.name.hashCode() + this.value.hashCode();
	}
    }

    public class GithubWebhookPayloadJenkinsCommitFlags {
        private ArrayList<GithubWebhookPayloadJenkinsFlag> jFlags;
	private String ref;
        private GithubWebhookPayloadPerson committer;

        public GithubWebhookPayloadJenkinsCommitFlags(String ref, GithubWebhookPayloadPerson committer, ArrayList<GithubWebhookPayloadJenkinsFlag> jFlags) {
            this.jFlags = jFlags;
            this.ref = ref;
            this.committer = committer;
        }

        public GithubWebhookPayloadJenkinsCommitFlags(String ref, GithubWebhookPayloadPerson committer) {
            this.jFlags = new ArrayList<GithubWebhookPayloadJenkinsFlag>();
            this.ref = ref;
            this.committer = committer;
        }

        public String getRef() {
            return ref;
        }

        public ArrayList<GithubWebhookPayloadJenkinsFlag> getJFlags() {
            return jFlags;
        }

        public GithubWebhookPayloadPerson getCommitter() {
            return committer;
        }
    }

    public class GithubWebhookPayloadPerson {
        private String name;
        private String username;
        private String email;

        public GithubWebhookPayloadPerson(String name, String username, String email) {
            this.name = name;
            this.username = username;
            this.email = email;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

    }

    public static final class DateTimeConverter implements JsonDeserializer<DateTime>, JsonSerializer<DateTime>
    {
       //static final org.joda.time.format.DateTimeFormatter DATE_TIME_FORMATTER = ISODateTimeFormat.dateTime().withZone(DateTimeZone.UTC);
    
       @Override
       public DateTime deserialize(final JsonElement je, final Type type, final JsonDeserializationContext jdc) throws JsonParseException {
          if (je.getAsString().length() == 0) {
              return null;
          }
          final DateTimeFormatter fmt = ISODateTimeFormat.dateTimeParser().withOffsetParsed();
          return fmt.parseDateTime(je.getAsString());
          //return je.getAsString().length() == 0 ? null : DATE_TIME_FORMATTER.parseDateTime(dateAsString);
       }
    
       @Override
       public JsonElement serialize(final DateTime src, final Type typeOfSrc, final JsonSerializationContext context) {
          final DateTimeFormatter fmt = ISODateTimeFormat.dateTime();
          return new JsonPrimitive(fmt.print(src));
          //return new JsonPrimitive(src == null ? StringUtils.EMPTY :DATE_TIME_FORMATTER.print(src)); 
       }
    }
    

}
