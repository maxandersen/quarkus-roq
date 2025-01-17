package io.quarkiverse.roq.frontmatter.deployment;

import static io.quarkiverse.roq.util.PathUtils.removeLeadingSlash;
import static io.quarkiverse.roq.util.PathUtils.removeTrailingSlash;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import io.quarkiverse.roq.util.PathUtils;
import io.vertx.core.json.JsonObject;

public class Link {
    public static final String DEFAULT_PAGE_LINK_TEMPLATE = "/:name";
    public static final String DEFAULT_PAGINATE_LINK_TEMPLATE = "/:collection/page:page";
    private static final DateTimeFormatter YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MM");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("dd");

    public interface LinkData {
        String baseFileName();

        String collection();

        ZonedDateTime date();

        JsonObject data();
    }

    public record PageLinkData(String baseFileName, ZonedDateTime date, String collection,
            JsonObject data) implements LinkData {
    }

    public record PaginateLinkData(String baseFileName, ZonedDateTime date, String collection, String page,
            JsonObject data) implements LinkData {
    }

    private static Map<String, Supplier<String>> withBasePlaceHolders(LinkData data, Map<String, Supplier<String>> other) {
        Map<String, Supplier<String>> result = new HashMap<>(Map.ofEntries(
                Map.entry(":collection", data::collection),
                Map.entry(":year", () -> Optional.ofNullable(data.date()).orElse(ZonedDateTime.now()).format(YEAR_FORMAT)),
                Map.entry(":month", () -> Optional.ofNullable(data.date()).orElse(ZonedDateTime.now()).format(MONTH_FORMAT)),
                Map.entry(":day", () -> Optional.ofNullable(data.date()).orElse(ZonedDateTime.now()).format(DAY_FORMAT)),
                Map.entry(":name", () -> slugify(data.baseFileName())),
                Map.entry(":title", () -> data.data().getString("slug", slugify(data.baseFileName())))));
        if (other != null) {
            result.putAll(other);
        }
        return result;
    }

    public static String pageLink(String rootPath, String template, PageLinkData data) {
        return link(rootPath, template, DEFAULT_PAGE_LINK_TEMPLATE, withBasePlaceHolders(data, null));
    }

    public static String paginateLink(String rootPath, String template, PaginateLinkData data) {
        return link(rootPath, template, DEFAULT_PAGINATE_LINK_TEMPLATE, withBasePlaceHolders(data, Map.of(
                ":page", () -> Objects.requireNonNull(data.page(), "page index is required to build the link"))));
    }

    public static String link(String rootPath, String template, String defaultTemplate, LinkData data,
            Map<String, Supplier<String>> placeHolders) {
        return link(rootPath, template, defaultTemplate, withBasePlaceHolders(data, placeHolders));
    }

    private static String link(String rootPath, String template, String defaultTemplate,
            Map<String, Supplier<String>> mapping) {
        String link = template != null ? template : defaultTemplate;
        // Replace each placeholder in the template if it exists
        for (Map.Entry<String, Supplier<String>> entry : mapping.entrySet()) {
            if (link.contains(entry.getKey())) {
                String replacement = entry.getValue().get();
                link = link.replace(entry.getKey(), replacement);
            }
        }

        if (link.endsWith("index") || link.endsWith("index.html")) {
            link = link.replaceAll("index(\\.html)?", "");
        }
        return removeTrailingSlash(removeLeadingSlash(PathUtils.join(rootPath, link)));
    }

    // Slugify logic to make the title URL-friendly
    public static String slugify(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Link input cannot be null");
        }
        return input.toLowerCase()
                .replaceAll("[^a-z0-9\\-]", "-") // Replace non-alphanumeric characters with hyphens
                .replaceAll("-+", "-") // Replace multiple hyphens with a single one
                .replaceAll("^-|-$", ""); // Remove leading/trailing hyphens
    }

}
