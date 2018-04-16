/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.spleefleague.entitybuilder;

import com.mongodb.client.MongoCollection;
import com.spleefleague.entitybuilder.EntityBuilder.IOClass.Input;
import com.spleefleague.entitybuilder.EntityBuilder.IOClass.Output;
import org.bson.Document;
import org.bson.types.ObjectId;
import sun.reflect.ReflectionFactory;

import java.lang.reflect.*;
import java.util.*;

/**
 * @author Jonas
 */
public class EntityBuilder {

    public static <T extends DBEntity & DBSaveable> void save(T object, MongoCollection<Document> dbcoll) {
        save(object, dbcoll, true);
    }

    public static <T extends DBEntity & DBSaveable> void save(T object, MongoCollection<Document> dbcoll, boolean override) {
        DBEntity dbe = (DBEntity) object;
        ObjectId _id = dbe.getObjectId();
        Document index = null;
        if (override && _id != null) {
            index = new Document("_id", _id);
        }
        Document dbo = serialize(object);
        validate(dbo);
        if (index != null) {
            dbcoll.updateOne(index, dbo);
        } else {
            dbo = (Document) dbo.get("$set");
            dbcoll.insertOne(dbo);
            _id = (ObjectId) dbcoll.find(dbo).first().get("_id");
            try {
                Field _idField = DBEntity.class.getDeclaredField("_id");
                _idField.setAccessible(true);
                _idField.set(dbe, _id);
            } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    private static void validate(Document dbo) {
        Document unset = (Document) dbo.get("$unset");
        if (unset != null) {
            if (unset.keySet().isEmpty()) {
                dbo.remove("$unset");
            }
        }
    }

    public static void delete(DBEntity object, MongoCollection<Document> dbcoll) {
        dbcoll.deleteOne(new Document("_id", object.getObjectId()));
    }

    public static Document serialize(DBSaveable object) {
        HashMap<String, Output> outputs = getOutputs(object.getClass());
        Document set = new Document();
        Document unset = new Document();
        Document query = new Document();
        outputs.entrySet()
                .stream()
                .sorted((e1, e2) -> e1.getValue().compareTo(e2.getValue()))
                .forEach(entry -> {
                    String name = entry.getKey();
                    Output out = entry.getValue();
                    Object o = out.get(object);
                    if (o != null) {
                        set.put(name, o);
                    } else {
                        unset.put(name, "");
                    }
                });
        query.put("$set", set);
        query.put("$unset", unset);
        return query;
    }

    public static <T extends DBEntity & DBLoadable> T load(Document dbo, Class<T> c) {
        return deserialize(dbo, c);
    }

    public static <T> T deserialize(Document dbo, Class<T> c) {
        T t = null;
        try {
            t = c.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            t = createInstance(c);
        }
        final T instance = t;
        Map<String, Input> inputs = getInputs(c);
        inputs.entrySet()
                .stream()
                .sorted((e1, e2) -> e1.getValue().compareTo(e2.getValue()))
                .forEach(entry -> {
                    String name = entry.getKey();
                    Input i = entry.getValue();
                    Object o = dbo.get(name);
                    if (o != null) {
                        i.set(instance, o);
                    }
                });
        ((DBLoadable) instance).done();
        return t;
    }

    private static Map<String, Input> getInputs(Class c) {
        Map<String, Input> inputs = new HashMap<>();
        Class current = c;
        while (current != null && current.isAssignableFrom(c)) {
            for (Method m : current.getDeclaredMethods()) {
                DBLoad dbload = m.getAnnotation(DBLoad.class);
                if (dbload != null) {
                    inputs.put(dbload.fieldName(), new Input(dbload.priority(), m));
                }

            }
            for (Field f : current.getDeclaredFields()) {
                DBLoad dbload = f.getAnnotation(DBLoad.class);
                if (dbload != null) {
                    inputs.put(dbload.fieldName(), new Input(dbload.priority(), f));
                }
            }
            current = current.getSuperclass();
        }
        return inputs;
    }

    private static HashMap<String, Output> getOutputs(Class c) {
        HashMap<String, Output> outputs = new HashMap<>();
        Class current = c;
        while (current != null && current.isAssignableFrom(c)) {
            for (Method m : current.getDeclaredMethods()) {
                DBSave dbsave = m.getAnnotation(DBSave.class);
                if (dbsave != null) {
                    outputs.put(dbsave.fieldName(), new Output(dbsave.priority(), m));
                }

            }
            for (Field f : current.getDeclaredFields()) {
                DBSave dbsave = f.getAnnotation(DBSave.class);
                if (dbsave != null) {
                    outputs.put(dbsave.fieldName(), new Output(dbsave.priority(), f));
                }
            }
            current = current.getSuperclass();
        }
        return outputs;
    }

    public static <T> T createInstance(Class<T> clazz) {
        try {
            return clazz.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            return create(clazz, Object.class);
        }
    }

    private static <T> T create(Class<T> c, Class<? super T> parent) {
        try {
            ReflectionFactory rf = ReflectionFactory.getReflectionFactory();
            Constructor objDef = parent.getDeclaredConstructor();
            Constructor intConstr = rf.newConstructorForSerialization(c, objDef);
            return c.cast(intConstr.newInstance());
        } catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            return null;
        }
    }

    public static abstract class IOClass implements Comparable<IOClass> {

        private final Method method;
        private final Field field;
        private final int priority;

        public IOClass(int priority, Field field) {
            this.field = field;
            this.field.setAccessible(true);
            this.priority = priority;
            this.method = null;
        }

        public IOClass(int priority, Method method) {
            this.method = method;
            this.method.setAccessible(true);
            this.priority = priority;
            this.field = null;
        }

        public boolean isMethod() {
            return method != null;
        }

        public boolean isField() {
            return field != null;
        }

        public int getPriority() {
            return priority;
        }

        @Override
        public int compareTo(IOClass o) {
            if (priority > o.getPriority()) {
                return -1;
            } else if (priority < o.getPriority()) {
                return 1;
            } else if (isField()) {
                return (o.isField()) ? 0 : -1;
            } else {
                return (o.isMethod()) ? 0 : 1;
            }
        }

        public static class Output extends IOClass {

            public Output(int priority, Field field) {
                super(priority, field);
            }

            public Output(int priority, Method method) {
                super(priority, method);
            }

            public Object get(Object instance) {
                if (isField()) {
                    return getField(instance);
                } else {
                    return getMethod(instance);
                }
            }

            private Object getField(Object instance) {
                try {
                    Field f = super.field;
                    Object o = f.get(instance);
                    Class<? extends TypeConverter> tcc = f.getAnnotation(DBSave.class).typeConverter();
                    if(tcc == TypeConverter.class) {
                        tcc = null;
                    }
                    return parseObject(o, tcc);
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }

            private Object getMethod(Object instance) {
                try {
                    Method m = super.method;
                    Object o = m.invoke(instance);
                    Class<? extends TypeConverter> tcc = m.getAnnotation(DBSave.class).typeConverter();
                    if(tcc == TypeConverter.class) {
                        tcc = null;
                    }
                    return parseObject(o, tcc);
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }
            
            private Object parseObject(Object o, Class<? extends TypeConverter> tcc) throws InstantiationException, IllegalAccessException {
                if (o == null) {
                    return null;
                }
                if (o.getClass().isEnum()) {
                    o = o.toString();
                } else if (o.getClass().isArray()) {
                    List list = new ArrayList<>();
                    Object[] array = (Object[]) o;
                    if (tcc != null) {
                        TypeConverter tc = tcc.newInstance();
                        for (Object value : array) {
                            list.add(tc.convertSave(value));
                        }
                    } else {
                        for (Object value : array) {
                            if(value == null) continue;
                            if (DBSaveable.class.isAssignableFrom(value.getClass())) {
                                value = serialize((DBSaveable) value).get("$set");
                            }
                            list.add(value);
                        }
                    }
                    o = list;
                } else if (Collection.class.isAssignableFrom(o.getClass())) {
                    List list = new ArrayList<>();
                    Collection col = (Collection) o;
                    if (tcc != null) {
                        TypeConverter tc = tcc.newInstance();
                        for (Object value : col) {
                            list.add(tc.convertSave(value));
                        }
                    } else {
                        for (Object value : col) {
                            if(value == null) continue;
                            if (DBSaveable.class.isAssignableFrom(value.getClass())) {
                                value = serialize((DBSaveable) value).get("$set");
                            }
                            else {
                                value = parseObject(value, null);
                            }
                            list.add(value);
                        }
                    }
                    o = list;
                } else if (tcc != null) {
                    TypeConverter tc = tcc.newInstance();
                    o = tc.convertSave(o);
                } else if (DBSaveable.class.isAssignableFrom(o.getClass())) {
                    o = serialize((DBSaveable) o).get("$set");
                }
                return o;
            }
        }

        public static class Input extends IOClass {

            public Input(int priority, Field field) {
                super(priority, field);
            }

            public Input(int priority, Method method) {
                super(priority, method);
            }

            public void set(Object instance, Object value) {
                if (isField()) {
                    setField(instance, value);
                } else {
                    setMethod(instance, value);
                }
            }
            
            private void setField(Object instance, Object value) {
                try {
                    Field f = super.field;
                    if (f.getType().isEnum() && value instanceof String) {
                        f.set(instance, Enum.valueOf((Class<Enum>) f.getType(), (String) value));
                    } else if (f.getType().isArray() && value instanceof List) {
                        List list = (List) value;
                        f.set(
                                instance, loadArray(list, f.getType().getComponentType(),
                                        f.getAnnotation(DBLoad.class).typeConverter()
                                ));
                    } else if (Collection.class.isAssignableFrom(f.getType()) && value instanceof List) {
                        Class c = f.getType();
                        List list = (List) value;
                        Collection col = loadCollection(
                                list, c, (ParameterizedType) f.getGenericType(),
                                f.getAnnotation(DBLoad.class).typeConverter()
                        );
                        f.set(instance, col);
                    } else if (!f.getAnnotation(DBLoad.class).typeConverter().equals(TypeConverter.class)) {
                        TypeConverter tc = f.getAnnotation(DBLoad.class).typeConverter().newInstance();
                        f.set(instance, tc.convertLoad(value));
                    } else if (value instanceof Document && DBLoadable.class.isAssignableFrom(f.getType())) {
                        f.set(instance, deserialize((Document) value, f.getType()));
                    } else if (value instanceof Document && Document.class.isAssignableFrom(f.getType())) {
                        f.set(instance, value);
                    } else {
                        value = rewrap(f.getType(), value);
                        f.set(instance, value);
                    }
                } catch (Exception e) {
                    String fName = "<unknown>";
                    if (super.field != null) {
                        fName = super.field.getName();
                    }
                    System.err.println("EntityBuilder failed setting field: " + instance.getClass().getName() + "." + fName);
                    throw new RuntimeException(e);
                }
            }

            private void setMethod(Object instance, Object value) {
                try {
                    Method m = super.method;
                    Class<?> ptype = m.getParameterTypes()[0];
                    if (ptype.isEnum() && value instanceof String) {
                        m.invoke(instance, Enum.valueOf((Class<Enum>) ptype, (String) value));
                    } else if (m.getParameterTypes()[0].isArray() && value instanceof List) {
                        List list = (List) value;
                        m.invoke(
                                instance, loadArray(list, ptype.getComponentType(),
                                        m.getAnnotation(DBLoad.class).typeConverter()
                                ));
                    } else if (Collection.class.isAssignableFrom(ptype) && value instanceof List) {
                        Class c = ptype;
                        List list = (List) value;
                        Collection col = loadCollection(
                                list, c, (ParameterizedType) m.getGenericParameterTypes()[0],
                                m.getAnnotation(DBLoad.class).typeConverter()
                        );
                        m.invoke(instance, col);
                    } else if (!m.getAnnotation(DBLoad.class).typeConverter().equals(TypeConverter.class)) {
                        TypeConverter tc = m.getAnnotation(DBLoad.class).typeConverter().newInstance();
                        m.invoke(instance, tc.convertLoad(value));
                    } else if (value instanceof Document
                            && DBLoadable.class.isAssignableFrom(ptype)) {
                        m.invoke(instance, deserialize((Document) value, ptype));
                    } else if (value instanceof Document && Document.class.isAssignableFrom(ptype)) {
                        m.invoke(instance, value);
                    } else {
                        value = rewrap(ptype, value);
                        m.invoke(instance, value);
                    }
                } catch (Exception e) {
                    String mName = "<unknown>";
                    if (super.method != null) {
                        mName = super.method.getName();
                    }
                    System.err.println("EntityBuilder failed setting method: " + instance.getClass().getName() + "." + mName);
                    throw new RuntimeException(e);
                }
            }

            private static <T> Object loadArray(List data, Class<? extends T> type, Class<? extends TypeConverter> tcc) throws InstantiationException, IllegalAccessException {
                Object[] array = new Object[data.size()];
                for (int i = 0; i < data.size(); i++) {
                    Object o = data.get(i);
                    if (!tcc.equals(TypeConverter.class)) {
                        TypeConverter tc = tcc.newInstance();
                        o = tc.convertLoad(o);
                    } else if (o instanceof Document && DBLoadable.class.isAssignableFrom(type)) {
                        o = deserialize((Document) o, type);
                    } else if (type.isEnum() && o instanceof String) {
                        o = Enum.valueOf((Class<Enum>) type, (String)o);
                    }
                    o = rewrap(type, o);
                    array[i] = o;
                }
                return createGenericArray(array, type);
            }

            private static <T extends Collection> T loadCollection(Collection list, Class<? extends T> type, ParameterizedType ptype, Class<? extends TypeConverter> tcc) throws InstantiationException, IllegalAccessException {
                Collection col = createCollectionInstance(type);
                for (Object o : list) {
                    if (Collection.class.isAssignableFrom(o.getClass())) {
                        o = loadCollection(
                                (Collection) o, (Class<? extends T>) ptype.getRawType(),
                                (ParameterizedType) ptype.getActualTypeArguments()[0], tcc
                        );
                    } else if (!tcc.equals(TypeConverter.class)) {
                        TypeConverter tc = tcc.newInstance();
                        o = tc.convertLoad(o);
                    } else if (o instanceof Document) {
                        if (DBLoadable.class.isAssignableFrom((Class) ptype.getActualTypeArguments()[0])) {
                            o = deserialize(
                                    (Document) o, (Class<? extends DBLoadable>) ptype.getActualTypeArguments()[0]);
                        }
                    } else if (((Class)ptype.getActualTypeArguments()[0]).isEnum() && o instanceof String) {
                        o = Enum.valueOf((Class<Enum>) ptype.getActualTypeArguments()[0], (String)o);
                    }
                    o = rewrap((Class<?>) ptype.getActualTypeArguments()[0], o);
                    col.add(o);
                }
                return (T) col;
            }
            
            private static Object rewrap(Class<?> target, Object o) {
                if(!(target.isPrimitive() || Number.class.isAssignableFrom(target)) || !(o instanceof Number)) {
                    return o;
                }
                Number n = (Number)o;
                if(target == long.class) {
                    return n.longValue();
                }
                else if(target == int.class) {
                    return n.intValue();
                }
                else if(target == short.class) {
                    return n.shortValue();
                }
                else if(target == byte.class) {
                    return n.byteValue();
                }
                else if(target == Long.class) {
                    return (Long)n.longValue();
                }
                else if(target == Integer.class) {
                    return (Integer)n.intValue();
                }
                else if(target == Short.class) {
                    return (Short)n.shortValue();
                }
                else if(target == Byte.class) {
                    return (Byte)n.byteValue();
                }
                throw new UnsupportedOperationException("Unknown number type: " + target.getClass());
            }
            private static Object createGenericArray(Object[] values, Class<?> cast) {
                Object array = Array.newInstance(cast, values.length);
                if (!cast.isPrimitive()) {
                    for (int i = 0; i < values.length; i++) {
                        Object value = values[i];
                        ((Object[]) array)[i] = cast.cast(value);
                    }
                } else if (cast == int.class) {
                    array = new int[values.length];
                    for (int i = 0; i < values.length; i++) {
                        int value = (int) values[i];
                        ((int[]) array)[i] = value;
                    }
                } else if (cast == long.class) {
                    array = new int[values.length];
                    for (int i = 0; i < values.length; i++) {
                        long value = (long) values[i];
                        ((long[]) array)[i] = value;
                    }
                } else if (cast == short.class) {
                    array = new int[values.length];
                    for (int i = 0; i < values.length; i++) {
                        short value = (short) values[i];
                        ((short[]) array)[i] = value;
                    }
                } else if (cast == byte.class) {
                    array = new int[values.length];
                    for (int i = 0; i < values.length; i++) {
                        byte value = (byte) values[i];
                        ((byte[]) array)[i] = value;
                    }
                } else if (cast == char.class) {
                    array = new int[values.length];
                    for (int i = 0; i < values.length; i++) {
                        char value = (char) values[i];
                        ((char[]) array)[i] = value;
                    }
                } else if (cast == boolean.class) {
                    array = new int[values.length];
                    for (int i = 0; i < values.length; i++) {
                        boolean value = (boolean) values[i];
                        ((boolean[]) array)[i] = value;
                    }
                } else {
                    throw new RuntimeException("Unknown primitive: " + cast);
                }
                return array;
            }

            private static Collection createCollectionInstance(Class<? extends Collection> c) {
                if (Modifier.isInterface(c.getModifiers()) || Modifier.isAbstract(c.getModifiers())) {
                    if (Set.class.isAssignableFrom(c)) {
                        return new HashSet();
                    } else {
                        return new ArrayList<>();
                    }
                } else {
                    return createInstance(c);
                }
            }
        }
    }
}
