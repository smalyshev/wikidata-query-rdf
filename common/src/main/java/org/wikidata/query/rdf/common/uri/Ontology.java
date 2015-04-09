package org.wikidata.query.rdf.common.uri;

/**
 * Marks the kinds of things (items or properties).
 */
public class Ontology {
    public static final String NAMESPACE = "http://www.wikidata.org/ontology#";

    /**
     * Wikibase exports all items with an assertion that their RDF.TYPE is this
     * and we filter that out.
     */
    public static final String ITEM = NAMESPACE + "Item";
    /**
     * Wikibase exports all statements with an assertion that their RDF.TYPE is
     * this and we filter that out.
     */
    public static final String STATEMENT = NAMESPACE + "Statement";
    /**
     * Wikibase exports references with an assertion that their RDF.TYPE is this
     * and we filter that out.
     */
    public static final String REFERENCE = NAMESPACE + "Reference";
    /**
     * Wikibase exports values with an assertion that their RDF.TYPE is this and
     * we filter that out.
     */
    public static final String VALUE = NAMESPACE + "Value";

    /**
     * Wikibase exports dump information with this subject.
     */
    public static final Object DUMP = NAMESPACE + "Dump";

    /**
     * Predicate for marking Wikibase's Rank.
     *
     * @see <a href="http://www.wikidata.org/wiki/Help:Ranking">The
     *      documentation for ranking</a>
     */
    public static final String RANK = NAMESPACE + "rank";
    public static final String BEST_RANK = NAMESPACE + "BestRank";
    public static final String PREFERRED_RANK = NAMESPACE + "PreferredRank";
    public static final String NORMAL_RANK = NAMESPACE + "NormalRank";
    public static final String DEPRECATED_RANK = NAMESPACE + "DeprecatedRank";

    public static class Time {
        private static final String PREFIX = NAMESPACE + "time";
        /**
         * The actual value of the time. We will always load this value exactly
         * as wikibase exports it - never normalize it for precision, timezone,
         * or calendar model.
         */
        public static final String VALUE = PREFIX + "Time";
        /**
         * The precision of the time. Wikibase exports integers with specific
         * meanings: 0 - billion years, 1 - hundred million years, ..., 6 -
         * millennium, 7 - century, 8 - decade, 9 - year, 10 - month, 11 - day,
         * 12 - hour, 13 - minute, 14 - second.
         */
        public static final String PRECISION = PREFIX + "Precision";
        /**
         * Timezone in which the time was originally defined. A signed integer
         * representing offset from UTC in minutes.
         */
        public static final String TIMEZONE = PREFIX + "Timezone";
        // TODO we should check if we have to normalize the simple values to UTC
        // or if wikibase does that
        /**
         * Calendar model in which the date was defined.
         */
        public static final String CALENDAR_MODEL = PREFIX + "CalendarModel";
        // TODO normalize simple values in different calendar models into
        // Gregorian where possible

    }

    public static StringBuilder prefix(StringBuilder query) {
        return query.append("PREFIX ontology: <").append(NAMESPACE).append(">\n");
    }
}