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
import java.io.StringReader;
import java.io.Writer;

import org.apache.freemarker.core.model.ArgumentArrayLayout;
import org.apache.freemarker.core.model.ObjectWrapper;
import org.apache.freemarker.core.model.TemplateBooleanModel;
import org.apache.freemarker.core.model.TemplateDirectiveModel;
import org.apache.freemarker.core.model.TemplateFunctionModel;
import org.apache.freemarker.core.model.TemplateModel;
import org.apache.freemarker.core.model.TemplateStringModel;
import org.apache.freemarker.core.model.TemplateSequenceModel;
import org.apache.freemarker.core.model.impl.BeanModel;
import org.apache.freemarker.core.model.impl.DefaultObjectWrapper;
import org.apache.freemarker.core.model.impl.SimpleNumber;

class BuiltInsForStringsMisc {

    // Can't be instantiated
    private BuiltInsForStringsMisc() { }
    
    static class booleanBI extends BuiltInForString {
        @Override
        TemplateModel calculateResult(String s, Environment env)  throws TemplateException {
            final boolean b;
            if (s.equals(TemplateBooleanFormat.C_TRUE)) {
                b = true;
            } else if (s.equals(TemplateBooleanFormat.C_FALSE)) {
                b = false;
            } else if (s.equals(env.getTemplateBooleanFormat().getTrueStringValue())) {
                b = true;
            } else if (s.equals(env.getTemplateBooleanFormat().getFalseStringValue())) {
                b = false;
            } else {
                throw new TemplateException(this, env,
                        "Can't convert this string to boolean: ", new _DelayedJQuote(s));
            }
            return b ? TemplateBooleanModel.TRUE : TemplateBooleanModel.FALSE;
        }
    }

    static class evalBI extends OutputFormatBoundBuiltIn {
        
        @Override
        protected TemplateModel calculateResult(Environment env) throws TemplateException {
            return calculateResult(BuiltInForString.getTargetString(target, env), env);
        }
        
        TemplateModel calculateResult(String s, Environment env) throws TemplateException {
            Template parentTemplate = getTemplate();
            
            ASTExpression exp;
            try {
                try {
                    ParsingConfiguration pCfg = parentTemplate.getParsingConfiguration();
                    
                    SimpleCharStream simpleCharStream = new SimpleCharStream(
                            new StringReader("(" + s + ")"),
                            RUNTIME_EVAL_LINE_DISPLACEMENT, 1,
                            s.length() + 2);
                    simpleCharStream.setTabSize(pCfg.getTabSize());
                    FMParserTokenManager tkMan = new FMParserTokenManager(
                            simpleCharStream);
                    tkMan.SwitchTo(FMParserConstants.FM_EXPRESSION);

                    // pCfg.outputFormat+autoEscapingPolicy is exceptional: it's inherited from the lexical context
                    FMParser parser = new FMParser(
                            parentTemplate, false, tkMan,
                            pCfg, outputFormat, autoEscapingPolicy,
                            null);
                    
                    exp = parser.ASTExpression();
                } catch (TokenMgrError e) {
                    throw e.toParseException(parentTemplate);
                }
            } catch (ParseException e) {
                throw new TemplateException(this, env,
                        "Failed to \"?", key, "\" string with this error:\n\n",
                        MessageUtils.EMBEDDED_MESSAGE_BEGIN,
                        new _DelayedGetMessage(e),
                        MessageUtils.EMBEDDED_MESSAGE_END,
                        "\n\nThe failing expression:");
            }
            try {
                return exp.eval(env);
            } catch (TemplateException e) {
                throw new TemplateException(this, env,
                        "Failed to \"?", key, "\" string with this error:\n\n",
                        MessageUtils.EMBEDDED_MESSAGE_BEGIN,
                        new _DelayedGetMessageWithoutStackTop(e),
                        MessageUtils.EMBEDDED_MESSAGE_END,
                        "\n\nThe failing expression:");
            }
        }
        
    }
    
    /**
     * A method that takes a parameter and evaluates it as a string,
     * then treats that string as template source code and returns a
     * transform model that evaluates the template in place.
     * The template inherits the configuration and environment of the executing
     * template. By default, its name will be equal to 
     * <tt>executingTemplate.getLookupName() + "$anonymous_interpreted"</tt>. You can
     * specify another parameter to the method call in which case the
     * template name suffix is the specified id instead of "anonymous_interpreted".
     */
    static class interpretBI extends OutputFormatBoundBuiltIn {
        
        /**
         * Constructs a template on-the-fly and returns it embedded in a
         * {@link TemplateDirectiveModel}.
         * 
         * <p>The built-in has two arguments:
         * the arguments passed to the method. It can receive at
         * least one and at most two arguments, both must evaluate to a string.
         * The first string is interpreted as a template source code and a template
         * is built from it. The second (optional) is used to give the generated
         * template a name.
         * 
         * @return a {@link TemplateDirectiveModel} that when executed inside
         * a <tt>&lt;transform></tt> block will process the generated template
         * just as if it had been <tt>&lt;transform></tt>-ed at that point.
         */
        @Override
        protected TemplateModel calculateResult(Environment env) throws TemplateException {
            TemplateModel model = target.eval(env);
            ASTExpression sourceExpr = null;
            String id = "anonymous_interpreted";
            if (model instanceof TemplateSequenceModel) {
                sourceExpr = ((ASTExpression) new ASTExpDynamicKeyName(target, new ASTExpNumberLiteral(0))
                        .copyLocationFrom(target));
                if (((TemplateSequenceModel) model).size() > 1) {
                    id = ((ASTExpression) new ASTExpDynamicKeyName(target, new ASTExpNumberLiteral(1))
                            .copyLocationFrom(target)).evalAndCoerceToPlainText(env);
                }
            } else if (model instanceof TemplateStringModel) {
                sourceExpr = target;
            } else {
                throw MessageUtils.newUnexpectedOperandTypeException(
                        target, model,
                        "sequence or string", new Class[] { TemplateSequenceModel.class, TemplateStringModel.class },
                        null, env);
            }
            String templateSource = sourceExpr.evalAndCoerceToPlainText(env);
            Template parentTemplate = env.getCurrentTemplate();
            
            final Template interpretedTemplate;
            try {
                ParsingConfiguration pCfg = parentTemplate.getParsingConfiguration();
                // pCfg.outputFormat+autoEscapingPolicy is exceptional: it's inherited from the lexical context
                interpretedTemplate = new Template(
                        (parentTemplate.getLookupName() != null ? parentTemplate.getLookupName() : "nameless_template") + "->" + id,
                        null,
                        new StringReader(templateSource),
                        parentTemplate.getConfiguration(), parentTemplate.getTemplateConfiguration(),
                        outputFormat, autoEscapingPolicy,
                        null, null);
            } catch (IOException e) {
                throw new TemplateException(this, e, env,
                        "Template parsing with \"?", key, "\" has failed with this error:\n\n",
                        MessageUtils.EMBEDDED_MESSAGE_BEGIN,
                        new _DelayedGetMessage(e),
                        MessageUtils.EMBEDDED_MESSAGE_END,
                        "\n\nThe failed expression:");
            }
            
            return new TemplateProcessorModel(interpretedTemplate);
        }

        private class TemplateProcessorModel implements TemplateDirectiveModel {
            private final Template template;
            
            TemplateProcessorModel(Template template) {
                this.template = template;
            }

            @Override
            public void execute(TemplateModel[] args, CallPlace callPlace, Writer out, Environment env)
                    throws TemplateException, IOException {
                try {
                    boolean lastFIRE = env.setFastInvalidReferenceExceptions(false);
                    try {
                        env.include(template);
                    } finally {
                        env.setFastInvalidReferenceExceptions(lastFIRE);
                    }
                } catch (Exception e) {
                    throw new TemplateException(e,
                            "Template created with \"?", key, "\" has stopped with this error:\n\n",
                            MessageUtils.EMBEDDED_MESSAGE_BEGIN,
                            new _DelayedGetMessage(e),
                            MessageUtils.EMBEDDED_MESSAGE_END);
                }
                callPlace.executeNestedContent(null, out, env);
            }

            @Override
            public ArgumentArrayLayout getDirectiveArgumentArrayLayout() {
                return ArgumentArrayLayout.PARAMETERLESS;
            }

            @Override
            public boolean isNestedContentSupported() {
                return false;
            }
        }

    }

    static class numberBI extends BuiltInForString {
        @Override
        TemplateModel calculateResult(String s, Environment env)  throws TemplateException {
            try {
                return new SimpleNumber(env.getArithmeticEngine().toNumber(s));
            } catch (NumberFormatException nfe) {
                throw new TemplateException(
                        new _ErrorDescriptionBuilder(
                                "Can't convert this string to number: ", new _DelayedJQuote(s))
                        .blame(this));
            }
        }
    }

    /**
     * A built-in that allows us to instantiate an instance of a java class.
     * Usage is something like: <tt>&lt;#assign foobar = "foo.bar.MyClass"?new()></tt>;
     */
    static class newBI extends ASTExpBuiltIn {
        
        @Override
        TemplateModel _eval(Environment env)
                throws TemplateException {
            return new ConstructorFunction(target.evalAndCoerceToPlainText(env), env, target.getTemplate());
        }

        class ConstructorFunction extends BuiltInCallableImpl implements TemplateFunctionModel {

            private final Class<?> cl;
            private final Environment env;
            
            ConstructorFunction(String classname, Environment env, Template template) throws TemplateException {
                this.env = env;
                cl = env.getNewBuiltinClassResolver().resolve(classname, env, template);
                if (!TemplateModel.class.isAssignableFrom(cl)) {
                    throw new TemplateException(newBI.this, env,
                            "Class ", cl.getName(), " does not implement org.apache.freemarker.core.TemplateModel");
                }
                if (BeanModel.class.isAssignableFrom(cl)) {
                    throw new TemplateException(newBI.this, env,
                            "Bean Models cannot be instantiated using the ?", key, " built-in");
                }
            }

            @Override
            public TemplateModel execute(TemplateModel[] args, CallPlace callPlace, Environment env)
                    throws TemplateException {
                ObjectWrapper ow = env.getObjectWrapper();
                if (ow instanceof DefaultObjectWrapper) {
                    return ow.wrap(((DefaultObjectWrapper) ow).newInstance(cl, args, callPlace));
                }

                if (args.length != 0) {
                    throw new TemplateException(
                            "className?new(args) only supports 0 arguments in the current configuration, because "
                            + " the objectWrapper setting value is not a "
                            + DefaultObjectWrapper.class.getName() +
                            " (or its subclass).");
                }
                try {
                    return ow.wrap(cl.newInstance());
                } catch (Exception e) {
                    throw new TemplateException("Failed to instantiate "
                            + cl.getName() + " with its parameterless constructor; see cause exception", e);
                }
            }

            @Override
            public ArgumentArrayLayout getFunctionArgumentArrayLayout() {
                return null;
            }

        }
    }
    
}
