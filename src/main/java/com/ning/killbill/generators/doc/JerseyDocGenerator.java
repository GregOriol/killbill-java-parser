package com.ning.killbill.generators.doc;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.ning.killbill.com.ning.killbill.args.KillbillParserArgs;
import com.ning.killbill.generators.BaseGenerator;
import com.ning.killbill.generators.Generator;
import com.ning.killbill.generators.GeneratorException;
import com.ning.killbill.objects.Annotation;
import com.ning.killbill.objects.ClassEnumOrInterface;
import com.ning.killbill.objects.Constructor;
import com.ning.killbill.objects.Field;
import com.ning.killbill.objects.Method;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class JerseyDocGenerator extends BaseGenerator implements Generator {


    @Override
    public void generate(KillbillParserArgs args) throws GeneratorException {


        final List<ClassEnumOrInterface> allGeneratedClasses = new ArrayList<ClassEnumOrInterface>();

        final List<URI> input = args.getInput();
        Writer w = null;
        try {

            try {
                final File docFile = createDocFile(args.getOutputDir());
                w = new FileWriter(docFile, true);

                parseAll(args, input);

                for (ClassEnumOrInterface cur : allClasses) {

                    if (isClassExcluded(cur.getFullName(), args.getClassGeneratorExcludes())) {
                        continue;
                    }

                    final List<Annotation> annotations = cur.getAnnotations();
                    if (!containsPathAnnotation(annotations)) {
                        continue;
                    }
                    generateClass(cur, allClasses, w);
                    allGeneratedClasses.add(cur);
                }
            } finally {
                w.close();
            }
        } catch (IOException io) {
            throw new GeneratorException("Failed to generate code: ", io);
        }
    }

    private File createDocFile(File outputDir) throws IOException {
        final File doc = new File(outputDir, "jersey.doc");
        doc.createNewFile();
        return doc;
    }

    private void generateClass(ClassEnumOrInterface cur, List<ClassEnumOrInterface> allClasses, Writer writer) throws IOException, GeneratorException {
        writer.write("****************************** " + cur.getName() + " ******************************\n");
        final List<Annotation> classAnnotations = cur.getAnnotations();
        final Annotation classPathAnnotation = getPathAnnotation(classAnnotations);
        final String pathPrefix = classPathAnnotation.getValue();

        for (Method m : cur.getMethods()) {
            Annotation httpVerbAnnotation = getHttpMethodAnnotation(m.getAnnotations());
            if (httpVerbAnnotation == null) {
                continue;
            }
            generateAPISignature(m, pathPrefix, httpVerbAnnotation.getName(), writer);
            generateJsonIfRequired(m, allClasses, httpVerbAnnotation.getName(), writer);
        }
    }

    private void generateAPISignature(final Method method, final String pathPrefix, final String verb, final Writer w) throws IOException {

        final Annotation methodPathAnnotation = getPathAnnotation(method.getAnnotations());
        final String path = pathPrefix +
                (methodPathAnnotation != null ? methodPathAnnotation.getValue() : "");
        w.write(verb);
        w.write(" ");
        w.write(path);
        w.write("\n");
        w.write("\n");
        w.flush();
    }

    private void generateJsonIfRequired(final Method method, List<ClassEnumOrInterface> allClasses, final String verb, final Writer w) throws IOException, GeneratorException {
        final List<Field> arguments = method.getOrderedArguments();
        final Field firstArgument = arguments.size() >= 1 ? arguments.get(0) : null;
        if (verb.equals("GET") ||
                firstArgument == null ||
                firstArgument.getAnnotations().size() != 0) {
            return;
        }
        final ClassEnumOrInterface jsonClass = findClassEnumOrInterface(firstArgument.getType().getBaseType(), allClasses);
        final Constructor ctor = getJsonCreatorCTOR(jsonClass);

        w.write("--------  Json Body:  --------\n");
        for (Field f : ctor.getOrderedArguments()) {
            final String attribute = getJsonPropertyAnnotationValue(jsonClass, f);
            w.write("\t" + attribute + "\n");
        }
        w.flush();
    }

    private boolean containsPathAnnotation(final List<Annotation> annotations) {

        if (annotations == null ||
                annotations.size() == 0) {
            return false;
        }
        if (getPathAnnotation(annotations) != null) {
            return true;
        }
        return false;
    }

    private Annotation getPathAnnotation(List<Annotation> annotations) {
        for (final Annotation cur : annotations) {
            if (cur.getName().equals("Path")) {
                return cur;
            }
        }
        return null;
    }

    private Annotation getHttpMethodAnnotation(List<Annotation> annotations) {
        Collection<Annotation> filtered = Collections2.filter(annotations, new Predicate<Annotation>() {
            @Override
            public boolean apply(Annotation input) {
                final String name = input.getName();
                return (name.equals("GET") ||
                        name.equals("PUT") ||
                        name.equals("DELETE") ||
                        name.equals("POST"));
            }
        });
        return filtered.iterator().hasNext() ? filtered.iterator().next() : null;
    }



    //
    // TODO those methods should be moved up; already exists somewhere else
    //
    private ClassEnumOrInterface findClassEnumOrInterface(final String fullyQualifiedName, final List<ClassEnumOrInterface> allClasses) throws GeneratorException {
        for (final ClassEnumOrInterface cur : allClasses) {
            if (cur.getFullName().equals(fullyQualifiedName)) {
                return cur;
            }
        }
        throw new GeneratorException("Cannot find classEnumOrInterface " + fullyQualifiedName);
    }

    private Constructor getJsonCreatorCTOR(final ClassEnumOrInterface obj) throws GeneratorException {
        final List<Constructor> ctors = obj.getCtors();
        for (Constructor cur : ctors) {
            if (cur.getAnnotations() == null || cur.getAnnotations().size() == 0) {
                continue;
            }
            for (final Annotation a : cur.getAnnotations()) {
                if ("JsonCreator".equals(a.getName())) {
                    return cur;
                }
            }
        }
        throw new GeneratorException("Could not find a CTOR for " + obj.getName() + " with a JsonCreator annotation");
    }

    private String getJsonPropertyAnnotationValue(final ClassEnumOrInterface obj, final Field f) throws GeneratorException {
        for (Annotation a : f.getAnnotations()) {
            if ("JsonProperty".equals(a.getName())) {
                return a.getValue();
            }
        }
        throw new GeneratorException("Could not find a JsonProperty annotation for object " + obj.getName() + " and field " + f.getName());
    }
}
