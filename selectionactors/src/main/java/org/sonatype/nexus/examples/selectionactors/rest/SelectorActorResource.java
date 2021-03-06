/*
 * Copyright (c) 2007-2012 Sonatype, Inc. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */

package org.sonatype.nexus.examples.selectionactors.rest;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.nexus.examples.selectionactors.Actor;
import org.sonatype.nexus.examples.selectionactors.Selection;
import org.sonatype.nexus.examples.selectionactors.Selector;
import org.sonatype.nexus.examples.selectionactors.model.RunReportDTO;
import org.sonatype.nexus.proxy.NoSuchRepositoryException;
import org.sonatype.nexus.proxy.repository.Repository;
import org.sonatype.nexus.rest.AbstractNexusPlexusResource;
import org.sonatype.plexus.rest.resource.PathProtectionDescriptor;

import com.google.common.base.Preconditions;
import com.thoughtworks.xstream.XStream;

import org.restlet.Context;
import org.restlet.data.Form;
import org.restlet.data.Parameter;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.ResourceException;
import org.restlet.resource.Variant;

/**
 * ???
 *
 * @since 1.0
 */
@Named
@Singleton
public class SelectorActorResource
    extends AbstractNexusPlexusResource
{
  private static final String REPOSITORY_ID = "repositoryId";

  private static final String SELECTOR_ID = "selectorId";

  private static final String ACTOR_ID = "actorId";

  private final Map<String, Selector> selectors;

  private final Map<String, Actor> actors;

  @Inject
  public SelectorActorResource(final Map<String, Selector> selectors, final Map<String, Actor> actors) {
    this.selectors = Preconditions.checkNotNull(selectors);
    this.actors = Preconditions.checkNotNull(actors);
    setReadable(true);
    setModifiable(false);
  }

  @Override
  public String getResourceUri() {
    return "/select/{" + REPOSITORY_ID + "}/{" + SELECTOR_ID + "}/{" + ACTOR_ID + "}";
  }

  @Override
  public Object getPayloadInstance() {
    return null;
  }

  @Override
  public PathProtectionDescriptor getResourceProtection() {
    return new PathProtectionDescriptor("/select/*/*/*", "authcBasic");
  }

  @Override
  public void configureXStream(XStream xstream) {
    super.configureXStream(xstream);
    xstream.processAnnotations(RunReportDTO.class);
  }

  public Object get(final Context context, final Request request, final Response response, final Variant variant)
      throws ResourceException
  {
    try {
      final Form form = request.getResourceRef().getQueryAsForm();
      final Map<String, String> terms = new HashMap<String, String>();
      for (Parameter parameter : form) {
        String paramName = parameter.getName();
        if (paramName.startsWith("t_") && paramName.length() > 2) {
          terms.put(paramName.substring(2), parameter.getValue());
        }
      }

      final String selectorKey = request.getAttributes().get(SELECTOR_ID).toString();
      final Selector selector = selectors.get(selectorKey);
      if (selector == null) {
        throw new IllegalArgumentException("Selector not found!");
      }

      final String actorKey = request.getAttributes().get(ACTOR_ID).toString();
      final Actor actor = actors.get(actorKey);
      if (actor == null) {
        throw new IllegalArgumentException("Actor not found!");
      }

      final Repository repository;
      try {
        repository =
            getRepositoryRegistry().getRepository(request.getAttributes().get(REPOSITORY_ID).toString());
      }
      catch (NoSuchRepositoryException e) {
        throw new IllegalArgumentException("Repository not found!");
      }

      final Selection selection = selector.select(repository, terms);
      final int selectionSize = selection.size();
      final int actedSize = actor.perform(selection, terms);

      return new RunReportDTO(repository.getId(), selectorKey, selectionSize, actorKey, actedSize, true);
    }
    catch (IllegalArgumentException t) {
      throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, t);
    }
    catch (IOException t) {
      throw new ResourceException(Status.SERVER_ERROR_INTERNAL, t);
    }
  }

}
