package com.ejbtestjava.mesh;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * The parse table from the hop-budget contract
 * (end_point_blank_deploy/docs/superpowers/specs/2026-09-08-hop-budget-contract.md).
 *
 * <p>Most cases below are zero cases, and each of them is a way a runaway ring
 * could start if the default went the other way. The rest guard the opposite
 * failure — a value the contract says is a budget being read as junk, which
 * stops a chain early and is just as invisible.
 */
class HopBudgetTest {

    @Test
    @DisplayName("an absent header is 0, never unlimited")
    void absentHeaderIsZero() {
        assertEquals(0, HopBudget.parse(null));
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" parses to 0")
    @ValueSource(strings = {
            "",                 // empty
            "abc",              // letters
            "four",             // a word
            "1.5",              // decimal
            "0x4",              // hex
            "+4",               // Integer.parseInt("+4") returns 4; the contract says 0
            "-1",               // negative
            "-100",             // very negative
            "0",                // explicit zero
            " ",                // whitespace only
            "\t",
            "\n",
            "   \t \n ",
            "4abc",             // trailing junk
            "abc4",             // leading junk
            "4 5",              // two numbers
            "\u0664",     // Arabic-Indic four: Integer.parseInt accepts it, we must not
            "\uFF14",     // fullwidth four: likewise
            "1_000",            // Java numeric literal syntax is not a wire format
            "4.0",
            "1e3",
            "Infinity",
            "null",
    })
    void parsesToZero(String header) {
        assertEquals(0, HopBudget.parse(header));
    }

    @Test
    @DisplayName("a plain positive integer parses to itself")
    void parsesPositiveIntegers() {
        assertEquals(1, HopBudget.parse("1"));
        assertEquals(4, HopBudget.parse("4"));
        assertEquals(9, HopBudget.parse("09"));
        assertEquals(64, HopBudget.parse("64"));
    }

    @Test
    @DisplayName("leading and trailing ASCII whitespace is trimmed")
    void trimsAsciiWhitespace() {
        assertEquals(4, HopBudget.parse(" 4 "));
        assertEquals(4, HopBudget.parse("\t4\r\n"));
        assertEquals(4, HopBudget.parse("4\f"));
    }

    @Test
    @DisplayName("non-ASCII whitespace is not trimmed, so what it wraps stays junk")
    void doesNotTrimNonAsciiWhitespace() {
        assertEquals(0, HopBudget.parse("\u00A0" + "4"));  // no-break space
        assertEquals(0, HopBudget.parse("4" + "\u2003"));  // em space
    }

    @Test
    @DisplayName("budgets above 64 are clamped, not rejected")
    void clampsToSixtyFour() {
        assertEquals(64, HopBudget.parse("65"));
        assertEquals(64, HopBudget.parse("1000000"));
        assertEquals(64, HopBudget.parse(" 1000000 "));
    }

    @Test
    @DisplayName("a budget too large for an int clamps rather than throwing")
    void clampsValuesBeyondIntRange() {
        assertEquals(64, HopBudget.parse("2147483648"));                     // Integer.MAX_VALUE + 1
        assertEquals(64, HopBudget.parse("99999999999999999999999999999"));  // beyond long, too
    }

    // ------------------------------------------------------ repeated headers

    /**
     * "Take the value before the first comma, then apply the rules above."
     *
     * <p>A repeated header can arrive already folded — PEP 3333 permits it and
     * waitress, which {@code epb_test_py} runs under, does it — so every
     * implementation must behave as though it had been. Reading the comma as
     * junk and answering 0 would be a <b>silent stop</b>: the chain terminates
     * early and the load driver sees fewer hops than it asked for, with nothing
     * to distinguish that from a budget that legitimately ran out.
     */
    static Stream<Arguments> commaFoldedValues() {
        return Stream.of(
                arguments("4, 8", 4),          // the contract's own example
                arguments("4,8", 4),           // no space after the comma
                arguments("1,2", 1),
                arguments("4,", 4),            // trailing comma: the second field is empty, not missing
                arguments(" 4 , 8 ", 4),       // trimming happens after the split, not before
                arguments("4, abc", 4),        // junk after the comma cannot poison the first value
                arguments("4,8,15", 4),        // only the FIRST comma matters
                arguments("09,2", 9),
                arguments("64,1", 64),
                arguments("1000000,1", 64),    // the clamp still applies to what the split yields
                arguments("9".repeat(400) + ",1", 64));
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" parses to {1}")
    @MethodSource("commaFoldedValues")
    void takesTheValueBeforeTheFirstComma(String header, int expected) {
        assertEquals(expected, HopBudget.parse(header));
    }

    /**
     * The split is not a licence to salvage. Once the first field is taken, it
     * faces the same table as any other value, so junk before the comma is
     * still 0 no matter how valid the rest of the value looks.
     */
    @ParameterizedTest(name = "[{index}] \"{0}\" parses to 0 despite a valid value after the comma")
    @ValueSource(strings = {
            "abc,4",            // the contract's own example
            ",4",               // empty first field
            ",",                // nothing either side
            " ,4",              // whitespace-only first field
            "+4,8",
            "-1,4",
            "0,4",              // an explicit zero is still a stop
            "1.5,4",
            "4 5,8",            // two numbers before the comma stay junk
            "4abc,8",
            "\u00A04,8",  // NBSP is not trimmed, so the first field stays junk
            "\u0664,4",   // Arabic-Indic four, still not a digit here
    })
    void junkBeforeTheCommaIsStillZero(String header) {
        assertEquals(0, HopBudget.parse(header));
    }
}
