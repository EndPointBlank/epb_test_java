package com.ejbtestjava.mesh;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The parse table from the hop-budget contract
 * (end_point_blank_deploy/docs/superpowers/specs/2026-09-08-hop-budget-contract.md).
 *
 * <p>Every case below is a zero case, and each of them is a way a runaway ring
 * could start if the default went the other way.
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
            "1,2",              // a comma-joined repeated header
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
}
