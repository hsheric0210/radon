package me.itzsomebody.radon.utils;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.MalformedParameterizedTypeException;
import java.lang.reflect.MalformedParametersException;
import java.lang.reflect.UndeclaredThrowableException;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

public final class Throwables
{
	private static final List<String> throwables;

	public static final String NullPointerException;
	public static final String IllegalArgumentException;

	static
	{
		throwables = new ArrayList<>();

		throwables.add(Throwable.class.getName().replace('.', '/'));

		// *** Exceptions
		throwables.add(Exception.class.getName().replace('.', '/'));

		// java.lang
		throwables.add(ArithmeticException.class.getName().replace('.', '/'));
		throwables.add(ArrayIndexOutOfBoundsException.class.getName().replace('.', '/'));
		throwables.add(ArrayStoreException.class.getName().replace('.', '/'));
		throwables.add(ClassCastException.class.getName().replace('.', '/'));
		throwables.add(ClassNotFoundException.class.getName().replace('.', '/'));
		throwables.add(CloneNotSupportedException.class.getName().replace('.', '/'));
		throwables.add(EnumConstantNotPresentException.class.getName().replace('.', '/'));
		throwables.add(IllegalAccessException.class.getName().replace('.', '/'));
		throwables.add(IllegalArgumentException = IllegalArgumentException.class.getName().replace('.', '/'));
		throwables.add(IllegalMonitorStateException.class.getName().replace('.', '/'));
		throwables.add(IllegalStateException.class.getName().replace('.', '/'));
		throwables.add(IllegalThreadStateException.class.getName().replace('.', '/'));
		throwables.add(IndexOutOfBoundsException.class.getName().replace('.', '/'));
		throwables.add(InstantiationException.class.getName().replace('.', '/'));
		throwables.add(InterruptedException.class.getName().replace('.', '/'));
		throwables.add(NegativeArraySizeException.class.getName().replace('.', '/'));
		throwables.add(NoSuchFieldException.class.getName().replace('.', '/'));
		throwables.add(NoSuchMethodException.class.getName().replace('.', '/'));
		throwables.add(NullPointerException = NullPointerException.class.getName().replace('.', '/'));
		throwables.add(NumberFormatException.class.getName().replace('.', '/'));
		throwables.add(ReflectiveOperationException.class.getName().replace('.', '/'));
		throwables.add(RuntimeException.class.getName().replace('.', '/'));
		throwables.add(SecurityException.class.getName().replace('.', '/'));
		throwables.add(StringIndexOutOfBoundsException.class.getName().replace('.', '/'));
		throwables.add(TypeNotPresentException.class.getName().replace('.', '/'));
		throwables.add(UnsupportedOperationException.class.getName().replace('.', '/'));

		// java.lang.reflect
		throwables.add(InvocationTargetException.class.getName().replace('.', '/'));
		throwables.add(MalformedParameterizedTypeException.class.getName().replace('.', '/'));
		throwables.add(MalformedParametersException.class.getName().replace('.', '/'));
		throwables.add(UndeclaredThrowableException.class.getName().replace('.', '/'));

		// java.io
		throwables.add(CharConversionException.class.getName().replace('.', '/'));
		throwables.add(EOFException.class.getName().replace('.', '/'));
		throwables.add(FileNotFoundException.class.getName().replace('.', '/'));
		throwables.add(InterruptedIOException.class.getName().replace('.', '/'));
		throwables.add(InvalidClassException.class.getName().replace('.', '/'));
		throwables.add(InvalidObjectException.class.getName().replace('.', '/'));
		throwables.add(IOException.class.getName().replace('.', '/'));
		throwables.add(NotActiveException.class.getName().replace('.', '/'));
		throwables.add(NotSerializableException.class.getName().replace('.', '/'));
		throwables.add(ObjectStreamException.class.getName().replace('.', '/'));
		throwables.add(OptionalDataException.class.getName().replace('.', '/'));
		throwables.add(StreamCorruptedException.class.getName().replace('.', '/'));
		throwables.add(SyncFailedException.class.getName().replace('.', '/'));
		throwables.add(UncheckedIOException.class.getName().replace('.', '/'));
		throwables.add(UnsupportedEncodingException.class.getName().replace('.', '/'));
		throwables.add(UTFDataFormatException.class.getName().replace('.', '/'));
		throwables.add(WriteAbortedException.class.getName().replace('.', '/'));

		// java.net
		throwables.add(BindException.class.getName().replace('.', '/'));
		throwables.add(ConnectException.class.getName().replace('.', '/'));
		throwables.add(HttpRetryException.class.getName().replace('.', '/'));
		throwables.add(MalformedURLException.class.getName().replace('.', '/'));
		throwables.add(NoRouteToHostException.class.getName().replace('.', '/'));
		throwables.add(PortUnreachableException.class.getName().replace('.', '/'));
		throwables.add(ProtocolException.class.getName().replace('.', '/'));
		throwables.add(SocketException.class.getName().replace('.', '/'));
		throwables.add(SocketTimeoutException.class.getName().replace('.', '/'));
		throwables.add(UnknownHostException.class.getName().replace('.', '/'));
		throwables.add(UnknownServiceException.class.getName().replace('.', '/'));
		throwables.add(URISyntaxException.class.getName().replace('.', '/'));

		// *** Errors
		throwables.add(Error.class.getName().replace('.', '/'));

		// java.lang
		throwables.add(AbstractMethodError.class.getName().replace('.', '/'));
		throwables.add(AssertionError.class.getName().replace('.', '/'));
		throwables.add(BootstrapMethodError.class.getName().replace('.', '/'));
		throwables.add(ClassCircularityError.class.getName().replace('.', '/'));
		throwables.add(ClassFormatError.class.getName().replace('.', '/'));
		throwables.add(ExceptionInInitializerError.class.getName().replace('.', '/'));
		throwables.add(IllegalAccessError.class.getName().replace('.', '/'));
		throwables.add(IncompatibleClassChangeError.class.getName().replace('.', '/'));
		throwables.add(InstantiationError.class.getName().replace('.', '/'));
		throwables.add(InternalError.class.getName().replace('.', '/'));
		throwables.add(LinkageError.class.getName().replace('.', '/'));
		throwables.add(NoClassDefFoundError.class.getName().replace('.', '/'));
		throwables.add(NoSuchFieldError.class.getName().replace('.', '/'));
		throwables.add(NoSuchMethodError.class.getName().replace('.', '/'));
		throwables.add(OutOfMemoryError.class.getName().replace('.', '/'));
		throwables.add(StackOverflowError.class.getName().replace('.', '/'));
		throwables.add(UnknownError.class.getName().replace('.', '/'));
		throwables.add(UnsatisfiedLinkError.class.getName().replace('.', '/'));
		throwables.add(UnsupportedClassVersionError.class.getName().replace('.', '/'));
		throwables.add(VerifyError.class.getName().replace('.', '/'));
		throwables.add(VirtualMachineError.class.getName().replace('.', '/'));
	}

	private Throwables()
	{

	}

	public static String getRandomThrowable()
	{
		return throwables.get(RandomUtils.getRandomInt(throwables.size()));
	}
}
