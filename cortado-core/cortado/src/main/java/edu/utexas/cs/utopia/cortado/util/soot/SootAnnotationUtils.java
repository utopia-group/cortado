package edu.utexas.cs.utopia.cortado.util.soot;

import soot.SootMethod;
import soot.tagkit.AnnotationElem;
import soot.tagkit.AnnotationStringElem;
import soot.tagkit.AnnotationTag;
import soot.tagkit.VisibilityAnnotationTag;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utils to deal with java annotations use {@link soot}.
 */
public class SootAnnotationUtils
{
    /**
     * Extract and return the {@link AnnotationTag} corresponding
     * to an annotation of type annotationType.
     *
     * The annotation must appear once, or not at all.
     *
     * @param method the method to get the annotation from
     * @return the annotation, or null if there is no annotation.
     */
    @Nullable
    public static AnnotationTag getAnnotation(@Nonnull SootMethod method, @Nonnull Class<?> annotationType)
    {
        // Format the java type of the annotation to the soot string representation
        // of the type.
        // format comes from looking at debugger, there's no good documentation for this.
        final String sootAnnotationTypeString = "L" + annotationType.getName().replaceAll("\\.", "/") + ";";
        // get all the visibility annotation tags
        final List<VisibilityAnnotationTag> visibilityAnnotationTags = method.getTags()
                .stream()
                .filter(tag -> tag instanceof VisibilityAnnotationTag)
                .map(tag -> (VisibilityAnnotationTag) tag)
                .collect(Collectors.toList());

        // get the annotations of the desired type
        final List<AnnotationTag> annotations = visibilityAnnotationTags.stream()
                .map(VisibilityAnnotationTag::getAnnotations)
                .flatMap(Collection::stream)
                .filter(annotationTag -> annotationTag.getType().equals(sootAnnotationTypeString))
                .collect(Collectors.toList());
        if(annotations.size() > 1) {
            throw new IllegalArgumentException("At most one annotation of type " + sootAnnotationTypeString + " expected.");
        } else if(annotations.size() == 1) {
            return annotations.get(0);
        }
        return null;
    }

    /**
     * Get the value of the specified parameter from the annotation tag
     *
     * @param annotation the annotation
     * @param parameterName the name of the parameter. If present, its value must be
     *                      a string.
     * @return the value associated to the annotation, or null if there is no such
     *          value.
     */
    @Nullable
    public static String getStringParameter(@Nonnull AnnotationTag annotation, @Nonnull String parameterName)
    {
        final Collection<AnnotationElem> elems = annotation.getElems();
        final List<AnnotationElem> matchingElems = elems.stream()
                .filter(annotationElem -> annotationElem.getName().equals(parameterName))
                .collect(Collectors.toList());
        if(matchingElems.size() > 1)
        {
            throw new IllegalArgumentException("More than one parameter of " + annotation + " has name " + parameterName);
        }
        String result = null;
        if(matchingElems.size() > 0)
        {
            final AnnotationElem matchingElem = matchingElems.get(0);
            if(!(matchingElem instanceof AnnotationStringElem))
            {
                throw new IllegalArgumentException("Expected parameter of type " + AnnotationStringElem.class + " not " + matchingElem.getClass());
            }
            result = ((AnnotationStringElem) matchingElem).getValue();
        }
        return result;
    }
}
