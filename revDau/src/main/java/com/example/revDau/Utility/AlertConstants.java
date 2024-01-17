package com.example.revDau.Utility;

import java.util.Arrays;

public  class AlertConstants {

    public static final String TRIGGERED_VALUE = "triggered.value";
    public static final String SEVERITY = "severity";

    public enum Operator {
        GREATER_THAN(">"),
        GREATER_OR_EQUALS(">="),
        LESS_THAN("<"),
        LESS_OR_EQUALS("<="),
        EQUALS("=="),
        NOT_EQUALS("!=");

        private final String name;

        Operator(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public static Operator of(String name) {
            return Arrays.stream(values()).parallel().filter(e -> e.getName().equalsIgnoreCase(name)).findFirst().orElseThrow();
        }
    }

    /**
     * IMPORTANT: Severity.ordinal() matters in Alert Engine.
     * DON'T Change anything unless you know what you are doing.
     * Warning has ordinal  0
     * Major has ordinal    1
     * Critical has ordinal 2
     */
    public enum Severity {
        WARNING("warning"),     // 0
        MAJOR("major"),         // 1
        CRITICAL("critical");   // 2

        private final String name;

        Severity(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public int order() {
            return this.ordinal();
        }

        /**
         * @return Severity with  1 order lower
         */
        public Severity lower() {
            return this.order() == 0 ? null : values()[this.order() - 1];
        }


        /**
         * @return Severity with 1 order higher
         */
        public Severity higher() {
            return this.order() == values().length ? null : values()[this.order() + 1];
        }

        public static Severity of(String name) {
            return Arrays.stream(values()).parallel().filter(e -> e.getName().equalsIgnoreCase(name)).findFirst().orElseThrow();
        }

        public static Severity highest() {
            return CRITICAL;
        }
    }
}
