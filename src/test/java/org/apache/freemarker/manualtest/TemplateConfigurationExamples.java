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
package org.apache.freemarker.manualtest;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.apache.freemarker.core.Configuration;
import org.apache.freemarker.core.Template;
import org.apache.freemarker.core.TemplateConfiguration;
import org.apache.freemarker.core.outputformat.impl.HTMLOutputFormat;
import org.apache.freemarker.core.outputformat.impl.PlainTextOutputFormat;
import org.apache.freemarker.core.outputformat.impl.UndefinedOutputFormat;
import org.apache.freemarker.core.outputformat.impl.XMLOutputFormat;
import org.apache.freemarker.core.templateresolver.ConditionalTemplateConfigurationFactory;
import org.apache.freemarker.core.templateresolver.FileExtensionMatcher;
import org.apache.freemarker.core.templateresolver.FileNameGlobMatcher;
import org.apache.freemarker.core.templateresolver.FirstMatchTemplateConfigurationFactory;
import org.apache.freemarker.core.templateresolver.MergingTemplateConfigurationFactory;
import org.apache.freemarker.core.templateresolver.OrMatcher;
import org.apache.freemarker.core.templateresolver.PathGlobMatcher;
import org.apache.freemarker.core.util._DateUtil;
import org.junit.Test;

public class TemplateConfigurationExamples extends ExamplesTest {

    @Test
    public void example1() throws Exception {
        Configuration cfg = getConfiguration();

        addTemplate("t.xml", "");
        
        TemplateConfiguration.Builder tcbUTF8XML = new TemplateConfiguration.Builder();
        tcbUTF8XML.setSourceEncoding(StandardCharsets.UTF_8);
        tcbUTF8XML.setOutputFormat(XMLOutputFormat.INSTANCE);

        {
            cfg.setTemplateConfigurations(new ConditionalTemplateConfigurationFactory(
                    new FileExtensionMatcher("xml"), tcbUTF8XML.build()));
            
            Template t = cfg.getTemplate("t.xml");
            assertEquals(StandardCharsets.UTF_8, t.getSourceEncoding());
            assertEquals(XMLOutputFormat.INSTANCE, t.getOutputFormat());
        }

        {
            cfg.setTemplateConfigurations(null);
            cfg.setSettings(loadPropertiesFile("TemplateConfigurationExamples1.properties"));
            
            Template t = cfg.getTemplate("t.xml");
            assertEquals(StandardCharsets.UTF_8, t.getSourceEncoding());
            assertEquals(XMLOutputFormat.INSTANCE, t.getOutputFormat());
        }
    }

    @Test
    public void example2() throws Exception {
        Configuration cfg = getConfiguration();
        
        addTemplate("t.subject.ftl", "");
        addTemplate("mail/t.subject.ftl", "");
        addTemplate("mail/t.body.ftl", "");

        TemplateConfiguration.Builder tcbSubject = new TemplateConfiguration.Builder();
        tcbSubject.setOutputFormat(PlainTextOutputFormat.INSTANCE);
        
        TemplateConfiguration.Builder tcbBody = new TemplateConfiguration.Builder();
        tcbBody.setOutputFormat(HTMLOutputFormat.INSTANCE);
        
        cfg.setTemplateConfigurations(
                new ConditionalTemplateConfigurationFactory(
                        new PathGlobMatcher("mail/**"),
                        new FirstMatchTemplateConfigurationFactory(
                                new ConditionalTemplateConfigurationFactory(
                                        new FileNameGlobMatcher("*.subject.*"),
                                        tcbSubject.build()),
                                new ConditionalTemplateConfigurationFactory(
                                        new FileNameGlobMatcher("*.body.*"),
                                        tcbBody.build())
                                )
                                .noMatchErrorDetails("Mail template names must contain \".subject.\" or \".body.\"!")
                        ));
        
        assertEquals(UndefinedOutputFormat.INSTANCE, cfg.getTemplate("t.subject.ftl").getOutputFormat());
        assertEquals(PlainTextOutputFormat.INSTANCE, cfg.getTemplate("mail/t.subject.ftl").getOutputFormat());
        assertEquals(HTMLOutputFormat.INSTANCE, cfg.getTemplate("mail/t.body.ftl").getOutputFormat());
        
        // From properties:
        
        cfg.setTemplateConfigurations(null);
        cfg.setSettings(loadPropertiesFile("TemplateConfigurationExamples2.properties"));
        
        assertEquals(UndefinedOutputFormat.INSTANCE, cfg.getTemplate("t.subject.ftl").getOutputFormat());
        assertEquals(PlainTextOutputFormat.INSTANCE, cfg.getTemplate("mail/t.subject.ftl").getOutputFormat());
        assertEquals(HTMLOutputFormat.INSTANCE, cfg.getTemplate("mail/t.body.ftl").getOutputFormat());
    }

    @Test
    public void example3() throws Exception {
        Configuration cfg = getConfiguration();
        cfg.setSourceEncoding(StandardCharsets.ISO_8859_1);
        cfg.setSharedVariable("ts", new Date(1440431606011L));
        
        addTemplate("t.stats.html", "${ts?datetime} ${ts?date} ${ts?time}");
        addTemplate("t.html", "");
        addTemplate("t.htm", "");
        addTemplate("t.xml", "");
        addTemplate("mail/t.html", "");

        TemplateConfiguration.Builder tcbStats = new TemplateConfiguration.Builder();
        tcbStats.setDateTimeFormat("iso");
        tcbStats.setDateFormat("iso");
        tcbStats.setTimeFormat("iso");
        tcbStats.setTimeZone(_DateUtil.UTC);

        TemplateConfiguration.Builder tcbMail = new TemplateConfiguration.Builder();
        tcbMail.setSourceEncoding(StandardCharsets.UTF_8);
        
        TemplateConfiguration.Builder tcbHTML = new TemplateConfiguration.Builder();
        tcbHTML.setOutputFormat(HTMLOutputFormat.INSTANCE);
        
        TemplateConfiguration.Builder tcbXML = new TemplateConfiguration.Builder();
        tcbXML.setOutputFormat(XMLOutputFormat.INSTANCE);
        
        cfg.setTemplateConfigurations(
                new MergingTemplateConfigurationFactory(
                        new ConditionalTemplateConfigurationFactory(
                                new FileNameGlobMatcher("*.stats.*"),
                                tcbStats.build()),
                        new ConditionalTemplateConfigurationFactory(
                                new PathGlobMatcher("mail/**"),
                                tcbMail.build()),
                        new FirstMatchTemplateConfigurationFactory(
                                new ConditionalTemplateConfigurationFactory(
                                        new FileExtensionMatcher("xml"),
                                        tcbXML.build()),
                                new ConditionalTemplateConfigurationFactory(
                                        new OrMatcher(
                                                new FileExtensionMatcher("html"),
                                                new FileExtensionMatcher("htm")),
                                        tcbHTML.build())
                        ).allowNoMatch(true)
                )
        );
        
        assertEquals(HTMLOutputFormat.INSTANCE, cfg.getTemplate("t.html").getOutputFormat());
        assertEquals(StandardCharsets.ISO_8859_1, cfg.getTemplate("t.html").getSourceEncoding());
        assertEquals(HTMLOutputFormat.INSTANCE, cfg.getTemplate("t.htm").getOutputFormat());
        assertEquals(XMLOutputFormat.INSTANCE, cfg.getTemplate("t.xml").getOutputFormat());
        assertEquals(HTMLOutputFormat.INSTANCE, cfg.getTemplate("t.stats.html").getOutputFormat());
        assertOutputForNamed("t.stats.html", "2015-08-24T15:53:26.011Z 2015-08-24 15:53:26.011Z");
        assertEquals(StandardCharsets.UTF_8, cfg.getTemplate("mail/t.html").getSourceEncoding());
        
        // From properties:
        
        cfg.setTemplateConfigurations(null);
        cfg.setSettings(loadPropertiesFile("TemplateConfigurationExamples3.properties"));
        
        assertEquals(HTMLOutputFormat.INSTANCE, cfg.getTemplate("t.html").getOutputFormat());
        assertEquals(StandardCharsets.ISO_8859_1, cfg.getTemplate("t.html").getSourceEncoding());
        assertEquals(HTMLOutputFormat.INSTANCE, cfg.getTemplate("t.htm").getOutputFormat());
        assertEquals(XMLOutputFormat.INSTANCE, cfg.getTemplate("t.xml").getOutputFormat());
        assertEquals(HTMLOutputFormat.INSTANCE, cfg.getTemplate("t.stats.html").getOutputFormat());
        assertOutputForNamed("t.stats.html", "2015-08-24T15:53:26.011Z 2015-08-24 15:53:26.011Z");
        assertEquals(StandardCharsets.UTF_8, cfg.getTemplate("mail/t.html").getSourceEncoding());
    }
    
}
