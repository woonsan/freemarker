/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.freemarker.core;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;

import org.apache.freemarker.core.util._ArrayEnumeration;

/**
 * AST non-expression node superclass: Superclass of directive calls, interpolations, static text, top-level comments,
 * or other such non-expression node in the parsed template. Some information that can be found here can be accessed
 * through the {@link Environment#getCurrentDirectiveCallPlace()}, which is a published API, and thus promises backward
 * compatibility.
 */
// TODO [FM3] Get rid of "public" and thus the "_" prefix
abstract class ASTElement extends ASTNode {

    private static final int INITIAL_CHILD_BUFFER_CAPACITY = 6;

    private ASTElement parent;

    /**
     * Contains 1 or more nested elements with optional trailing {@code null}-s, or is {@code null} exactly if there are
     * no nested elements.
     */
    private ASTElement[] childBuffer;

    /**
     * Contains the number of elements in the {@link #childBuffer}, not counting the trailing {@code null}-s. If this is
     * 0, then and only then {@link #childBuffer} must be {@code null}.
     */
    private int childCount;

    /**
     * The index of the element in the parent's {@link #childBuffer} array.
     */
    private int index;

    /**
     * Executes this {@link ASTElement}. Usually should not be called directly, but through
     * {@link Environment#visit(ASTElement)} or a similar {@link Environment} method.
     *
     * @param env
     *            The runtime environment
     * 
     * @return The template elements to execute (meant to be used for nested elements), or {@code null}. Can have
     *         <em>trailing</em> {@code null}-s (unused buffer capacity). Returning the nested elements instead of
     *         executing them inside this method is a trick used for decreasing stack usage when there's nothing to do
     *         after the children was processed anyway.
     */
    abstract ASTElement[] accept(Environment env) throws TemplateException, IOException;

    /**
     * One-line description of the element, that contain all the information that is used in {@link #getCanonicalForm()}
     * , except the nested content (elements) of the element. The expressions inside the element (the parameters) has to
     * be shown. Meant to be used for stack traces, also for tree views that don't go down to the expression-level.
     * There are no backward-compatibility guarantees regarding the format used ATM, but it must be regular enough to be
     * machine-parseable, and it must contain all information necessary for restoring an AST equivalent to the original.
     * 
     * This final implementation calls {@link #dump(boolean) dump(false)}.
     * 
     * @see #getCanonicalForm()
     * @see #getASTNodeDescriptor()
     */
    public final String getDescription() {
        return dump(false);
    }

    /**
     * This final implementation calls {@link #dump(boolean) dump(false)}.
     */
    @Override
    public final String getCanonicalForm() {
        return dump(true);
    }

    final String getChildrenCanonicalForm() {
        return getChildrenCanonicalForm(childBuffer);
    }
    
    static String getChildrenCanonicalForm(ASTElement[] children) {
        if (children == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ASTElement child : children) {
            if (child == null) {
                break;
            }
            sb.append(child.getCanonicalForm());
        }
        return sb.toString();
    }

    /**
     * Tells if the element should show up in error stack traces. Note that this will be ignored for the top (current)
     * element of a stack trace, as that's always shown.
     */
    boolean isShownInStackTrace() {
        return false;
    }

    /**
     * Tells if this element possibly executes its nested content for many times. This flag is useful when a template
     * AST is modified for running time limiting (see {@link ThreadInterruptionSupportTemplatePostProcessor}). Elements
     * that use {@link #childBuffer} should not need this, as the insertion of the timeout checks is impossible there,
     * given their rigid nested element schema.
     */
    abstract boolean isNestedBlockRepeater();

    /**
     * Brings the implementation of {@link #getCanonicalForm()} and {@link #getDescription()} to a single place. Don't
     * call those methods in method on {@code this}, because that will result in infinite recursion!
     * 
     * @param canonical
     *            if {@code true}, it calculates the return value of {@link #getCanonicalForm()}, otherwise of
     *            {@link #getDescription()}.
     */
    abstract protected String dump(boolean canonical);

    // Methods to implement TemplateNodeModel

    public String getNodeName() {
        String className = getClass().getName();
        int shortNameOffset = className.lastIndexOf('.') + 1;
        return className.substring(shortNameOffset);
    }

    // Methods so that we can implement the Swing TreeNode API.

    public boolean isLeaf() {
        return childCount == 0;
    }

    public int getIndex(ASTElement node) {
        for (int i = 0; i < childCount; i++) {
            if (childBuffer[i].equals(node)) {
                return i;
            }
        }
        return -1;
    }

    public int getChildCount() {
        return childCount;
    }

    /**
     * Note: For element with {@code #nestedBlock}, this will hide the {@code #nestedBlock} when that's a
     * {@link ASTImplicitParent}.
     */
    public Enumeration children() {
        return childBuffer != null
                ? new _ArrayEnumeration(childBuffer, childCount)
                : Collections.enumeration(Collections.EMPTY_LIST);
    }

    public void setChildAt(int index, ASTElement element) {
        if (index < childCount && index >= 0) {
            childBuffer[index] = element;
            element.index = index;
            element.parent = this;
        } else {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + childCount);
        }
    }
    
    /**
     * The element whose child this element is, or {@code null} if this is the root node.
     */
    final ASTElement getParent() {
        return parent;
    }

    final void setChildBufferCapacity(int capacity) {
        int ln = childCount;
        ASTElement[] newChildBuffer = new ASTElement[capacity];
        for (int i = 0; i < ln; i++) {
            newChildBuffer[i] = childBuffer[i];
        }
        childBuffer = newChildBuffer;
    }

    /**
     * Inserts a new nested element after the last nested element.
     */
    final void addChild(ASTElement nestedElement) {
        addChild(childCount, nestedElement);
    }

    /**
     * Inserts a new nested element at the given index, which can also be one higher than the current highest index.
     */
    final void addChild(int index, ASTElement nestedElement) {
        final int lChildCount = childCount;

        ASTElement[] lChildBuffer = childBuffer;
        if (lChildBuffer == null) {
            lChildBuffer = new ASTElement[INITIAL_CHILD_BUFFER_CAPACITY];
            childBuffer = lChildBuffer;
        } else if (lChildCount == lChildBuffer.length) {
            setChildBufferCapacity(lChildCount != 0 ? lChildCount * 2 : 1);
            lChildBuffer = childBuffer;
        }
        // At this point: nestedElements == this.nestedElements, and has sufficient capacity.

        for (int i = lChildCount; i > index; i--) {
            ASTElement movedElement = lChildBuffer[i - 1];
            movedElement.index = i;
            lChildBuffer[i] = movedElement;
        }
        nestedElement.index = index;
        nestedElement.parent = this;
        lChildBuffer[index] = nestedElement;
        childCount = lChildCount + 1;
    }

    final ASTElement getChild(int index) {
        return childBuffer[index];
    }

    /**
     * @return Array containing 1 or more nested elements with optional trailing {@code null}-s, or is {@code null}
     *         exactly if there are no nested elements.
     */
    final ASTElement[] getChildBuffer() {
        return childBuffer;
    }

    /**
     * @param buffWithCnt Maybe {@code null}
     */
    final void setChildren(TemplateElements buffWithCnt) {
        ASTElement[] childBuffer = buffWithCnt.getBuffer();
        int childCount = buffWithCnt.getCount();
        for (int i = 0; i < childCount; i++) {
            ASTElement child = childBuffer[i];
            child.index = i;
            child.parent = this;
        }
        this.childBuffer = childBuffer;
        this.childCount = childCount;
    }

    final int getIndex() {
        return index;
    }

    /**
     * This is a special case, because a root element is not contained in another element, so we couldn't set the
     * private fields.
     */
    final void setFieldsForRootElement() {
        index = 0;
        parent = null;
    }

    /**
     * Walk the AST subtree rooted by this element, and do simplifications where possible, also removes superfluous
     * whitespace.
     * 
     * @param stripWhitespace
     *            whether to remove superfluous whitespace
     * 
     * @return The element this element should be replaced with in the parent. If it's the same as this element, no
     *         actual replacement will happen. Note that adjusting the {@link #parent} and {@link #index} of the result
     *         is the duty of the caller, not of this method.
     */
    ASTElement postParseCleanup(boolean stripWhitespace) throws ParseException {
        int childCount = this.childCount;
        if (childCount != 0) {
            for (int i = 0; i < childCount; i++) {
                ASTElement te = childBuffer[i];
                
                /*
                // Assertion:
                if (te.getIndex() != i) {
                    throw new BugException("Invalid index " + te.getIndex() + " (expected: "
                            + i + ") for: " + te.dump(false));
                }
                if (te.getParent() != this) {
                    throw new BugException("Invalid parent " + te.getParent() + " (expected: "
                            + this.dump(false) + ") for: " + te.dump(false));
                }
                */
                
                te = te.postParseCleanup(stripWhitespace);
                childBuffer[i] = te;
                te.parent = this;
                te.index = i;
            }
            for (int i = 0; i < childCount; i++) {
                ASTElement te = childBuffer[i];
                if (te.isIgnorable(stripWhitespace)) {
                    childCount--;
                    // As later isIgnorable calls might investigates the siblings, we have to move all the items now. 
                    for (int j = i; j < childCount; j++) {
                        final ASTElement te2 = childBuffer[j + 1];
                        childBuffer[j] = te2;
                        te2.index = j;
                    }
                    childBuffer[childCount] = null;
                    this.childCount = childCount;
                    i--;
                }
            }
            if (childCount == 0) {
                childBuffer = null;
            } else if (childCount < childBuffer.length
                    && childCount <= childBuffer.length * 3 / 4) {
                ASTElement[] trimmedChildBuffer = new ASTElement[childCount];
                for (int i = 0; i < childCount; i++) {
                    trimmedChildBuffer[i] = childBuffer[i];
                }
                childBuffer = trimmedChildBuffer;
            }
        }
        return this;
    }

    boolean isIgnorable(boolean stripWhitespace) {
        return false;
    }

    // The following methods exist to support some fancier tree-walking
    // and were introduced to support the whitespace cleanup feature in 2.2

    ASTElement prevTerminalNode() {
        ASTElement prev = previousSibling();
        if (prev != null) {
            return prev.getLastLeaf();
        } else if (parent != null) {
            return parent.prevTerminalNode();
        }
        return null;
    }

    ASTElement nextTerminalNode() {
        ASTElement next = nextSibling();
        if (next != null) {
            return next.getFirstLeaf();
        } else if (parent != null) {
            return parent.nextTerminalNode();
        }
        return null;
    }

    ASTElement previousSibling() {
        if (parent == null) {
            return null;
        }
        return index > 0 ? parent.childBuffer[index - 1] : null;
    }

    ASTElement nextSibling() {
        if (parent == null) {
            return null;
        }
        return index + 1 < parent.childCount ? parent.childBuffer[index + 1] : null;
    }

    private ASTElement getFirstChild() {
        return childCount == 0 ? null : childBuffer[0];
    }

    private ASTElement getLastChild() {
        final int childCount = this.childCount;
        return childCount == 0 ? null : childBuffer[childCount - 1];
    }

    private ASTElement getFirstLeaf() {
        ASTElement te = this;
        while (!te.isLeaf() && !(te instanceof ASTDirMacroOrFunction) && !(te instanceof ASTDirCapturingAssignment)) {
            // A macro or macro invocation is treated as a leaf here for special reasons
            te = te.getFirstChild();
        }
        return te;
    }

    private ASTElement getLastLeaf() {
        ASTElement te = this;
        while (!te.isLeaf() && !(te instanceof ASTDirMacroOrFunction) && !(te instanceof ASTDirCapturingAssignment)) {
            // A macro or macro invocation is treated as a leaf here for special reasons
            te = te.getLastChild();
        }
        return te;
    }

    /**
     * Tells if executing this element has output that only depends on the template content and that has no side
     * effects.
     */
    boolean isOutputCacheable() {
        return false;
    }

    boolean isChildrenOutputCacheable() {
        int ln = childCount;
        for (int i = 0; i < ln; i++) {
            if (!childBuffer[i].isOutputCacheable()) {
                return false;
            }
        }
        return true;
    }

    /**
     * determines whether this element's presence on a line indicates that we should not strip opening whitespace in the
     * post-parse whitespace gobbling step.
     */
    boolean heedsOpeningWhitespace() {
        return false;
    }

    /**
     * determines whether this element's presence on a line indicates that we should not strip trailing whitespace in
     * the post-parse whitespace gobbling step.
     */
    boolean heedsTrailingWhitespace() {
        return false;
    }
}
