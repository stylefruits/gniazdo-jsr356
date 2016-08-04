package gniazdo;

//
//  ========================================================================
//  Copyright (c) 1995-2016 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;


import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Provide some consistent Http header value and Extension configuration parameter quoting support.
 * <p>
 * While QuotedStringTokenizer exists in jetty-util, and works great with http header values, using it in websocket-api is undesired.
 * <ul>
 * <li>Using QuotedStringTokenizer would introduce a dependency to jetty-util that would need to be exposed via the WebAppContext classloader</li>
 * <li>ABNF defined extension parameter parsing requirements of RFC-6455 (WebSocket) ABNF, is slightly different than the ABNF parsing defined in RFC-2616
 * (HTTP/1.1).</li>
 * <li>Future HTTPbis ABNF changes for parsing will impact QuotedStringTokenizer</li>
 * </ul>
 * It was decided to keep this implementation separate for the above reasons.
 */
public class JettyQuoteUtil
{
    private static class DeQuotingStringIterator implements Iterator<String>
    {
        private enum State
        {
            START,
            TOKEN,
            QUOTE_SINGLE,
            QUOTE_DOUBLE
        }

        private final String input;
        private final String delims;
        private StringBuilder token;
        private boolean hasToken = false;
        private int i = 0;

        public DeQuotingStringIterator(String input, String delims)
        {
            this.input = input;
            this.delims = delims;
            int len = input.length();
            token = new StringBuilder(len > 1024?512:len / 2);
        }

        private void appendToken(char c)
        {
            if (hasToken)
            {
                token.append(c);
            }
            else
            {
                if (Character.isWhitespace(c))
                {
                    return; // skip whitespace at start of token.
                }
                else
                {
                    token.append(c);
                    hasToken = true;
                }
            }
        }

        public boolean hasNext()
        {
            // already found a token
            if (hasToken)
            {
                return true;
            }

            DeQuotingStringIterator.State state = DeQuotingStringIterator.State.START;
            boolean escape = false;
            int inputLen = input.length();

            while (i < inputLen)
            {
                char c = input.charAt(i++);

                switch (state)
                {
                    case START:
                    {
                        if (c == '\'')
                        {
                            state = DeQuotingStringIterator.State.QUOTE_SINGLE;
                            appendToken(c);
                        }
                        else if (c == '\"')
                        {
                            state = DeQuotingStringIterator.State.QUOTE_DOUBLE;
                            appendToken(c);
                        }
                        else
                        {
                            appendToken(c);
                            state = DeQuotingStringIterator.State.TOKEN;
                        }
                        break;
                    }
                    case TOKEN:
                    {
                        if (delims.indexOf(c) >= 0)
                        {
                            // System.out.printf("hasNext/t: %b [%s]%n",hasToken,token);
                            return hasToken;
                        }
                        else if (c == '\'')
                        {
                            state = DeQuotingStringIterator.State.QUOTE_SINGLE;
                        }
                        else if (c == '\"')
                        {
                            state = DeQuotingStringIterator.State.QUOTE_DOUBLE;
                        }
                        appendToken(c);
                        break;
                    }
                    case QUOTE_SINGLE:
                    {
                        if (escape)
                        {
                            escape = false;
                            appendToken(c);
                        }
                        else if (c == '\'')
                        {
                            appendToken(c);
                            state = DeQuotingStringIterator.State.TOKEN;
                        }
                        else if (c == '\\')
                        {
                            escape = true;
                        }
                        else
                        {
                            appendToken(c);
                        }
                        break;
                    }
                    case QUOTE_DOUBLE:
                    {
                        if (escape)
                        {
                            escape = false;
                            appendToken(c);
                        }
                        else if (c == '\"')
                        {
                            appendToken(c);
                            state = DeQuotingStringIterator.State.TOKEN;
                        }
                        else if (c == '\\')
                        {
                            escape = true;
                        }
                        else
                        {
                            appendToken(c);
                        }
                        break;
                    }
                }
                // System.out.printf("%s <%s> : [%s]%n",state,c,token);
            }
            // System.out.printf("hasNext/e: %b [%s]%n",hasToken,token);
            return hasToken;
        }

        public String next()
        {
            if (!hasNext())
            {
                throw new NoSuchElementException();
            }
            String ret = token.toString();
            token.setLength(0);
            hasToken = false;
            return dequote(ret.trim());
        }

        public void remove()
        {
            throw new UnsupportedOperationException("Remove not supported with this iterator");
        }
    }

    /**
     * Remove quotes from a string, only if the input string start with and end with the same quote character.
     *
     * @param str
     *            the string to remove surrounding quotes from
     * @return the de-quoted string
     */
    public static String dequote(String str)
    {
        char start = str.charAt(0);
        if ((start == '\'') || (start == '\"'))
        {
            // possibly quoted
            char end = str.charAt(str.length() - 1);
            if (start == end)
            {
                // dequote
                return str.substring(1,str.length() - 1);
            }
        }
        return str;
    }

    /**
     * Create an iterator of the input string, breaking apart the string at the provided delimiters, removing quotes and triming the parts of the string as
     * needed.
     *
     * @param str
     *            the input string to split apart
     * @param delims
     *            the delimiter characters to split the string on
     * @return the iterator of the parts of the string, trimmed, with quotes around the string part removed, and unescaped
     */
    public static Iterator<String> splitAt(String str, String delims)
    {
        return new DeQuotingStringIterator(str.trim(),delims);
    }

}
