package cl_util;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.List;

import lightLogger.Logger;
import clustering.ICPoint;
import clustering.IPoint;
import clustering.Point;

import com.nativelibs4java.opencl.CLBuffer;
import com.nativelibs4java.opencl.CLContext;
import com.nativelibs4java.opencl.CLEvent;
import com.nativelibs4java.opencl.CLException;
import com.nativelibs4java.opencl.CLKernel;
import com.nativelibs4java.opencl.CLMem;
import com.nativelibs4java.opencl.CLQueue;

public class CLPointFloat {

	private static final Class<CLPointFloat> CLAZZ = CLPointFloat.class;

	public static final int MAX_BUFFER_SIZE = 8192;
	private int BUFFER_SIZE;
	private int BUFFER_ITEMS;
	private static final int SIZEOF_CL_FLOAT = 4;

	private static final String PREFIX = CLAZZ.getSimpleName();
	private static final String KERNEL_DIST = "distFloat";
	private static final String KERNEL_PATH = "/kernel/CLPointFloat.cl";

	private final int DIM;
	private CLInstance clInstance;

	private ICPoint[] itemBuffer;
	private float[] buffer;
	private int itemCount;
	private int bufferCount;
	private CLBuffer<FloatBuffer> resultBuffer;
	private CLBuffer<FloatBuffer> pointBuffer;
	private CLBuffer<FloatBuffer> compareBuffer;
	private int COMPARE_ITEMS;

	public CLPointFloat(CLInstance clInstance, int dim) {
		this.clInstance = clInstance;
		this.DIM = dim;

		BUFFER_ITEMS = MAX_BUFFER_SIZE / DIM;
		BUFFER_SIZE = BUFFER_ITEMS * DIM;

		this.resetBuffer();
	}

	public void resetBuffer(int bufferItems) {
		BUFFER_ITEMS = (bufferItems * DIM) > MAX_BUFFER_SIZE ? (MAX_BUFFER_SIZE / DIM)
				: bufferItems * DIM;
		BUFFER_SIZE = BUFFER_ITEMS * DIM;

		this.itemBuffer = new ICPoint[BUFFER_ITEMS];
		this.buffer = new float[BUFFER_SIZE];
		this.itemCount = 0;
		this.bufferCount = 0;
		this.pointBuffer = this.clInstance.getContext().createFloatBuffer(
				CLMem.Usage.Input,

				BUFFER_SIZE);
		this.resultBuffer = this.clInstance.getContext().createFloatBuffer(
				CLMem.Usage.InputOutput, BUFFER_SIZE);
	}

	public void resetBuffer() {
		this.resetBuffer(BUFFER_ITEMS);
	}

	public void prepareNearestPoints(List<IPoint> centroids) {
		COMPARE_ITEMS = centroids.size();
		float[] centroidsBuffer = new float[COMPARE_ITEMS * DIM];

		int i = 0;
		for (IPoint c : centroids) {
			for (int d = 0; d < DIM; d++)
				centroidsBuffer[i++] = c.get(d);
		}

		try {
			CLContext context = this.clInstance.getContext();
			CLQueue cmdQ = this.clInstance.getQueue();

			this.compareBuffer = context.createFloatBuffer(CLMem.Usage.Input,
					FloatBuffer.wrap(centroidsBuffer), true);

			cmdQ.finish();
		} catch (CLException err) {
			Logger.logError(CLAZZ, "OpenCL error:\n" + err.getMessage() + "():"
					+ err.getCode());
			err.printStackTrace();
		} catch (Exception err) {
			Logger.logError(CLAZZ, "Error:\n" + err.getMessage() + "()");
			err.printStackTrace();
		}
	}

	public void put(ICPoint p) {
		if (this.itemCount < BUFFER_ITEMS) {
			this.itemBuffer[this.itemCount++] = p;
			
			for (int d = 0; d < DIM; d++)
				this.buffer[bufferCount++] = p.get(d);
		} else {
			doNearestPoints(this.itemCount);
			this.itemBuffer[this.itemCount++] = p;
			for (int d = 0; d < DIM; d++)
				this.buffer[bufferCount++] = p.get(d);
		}
	}

	public void setNearestPoints() {
		Logger.logTrace(CLAZZ, "setNearestPoints()");
		if (0 < this.itemCount || this.itemCount == BUFFER_ITEMS)
			this.doNearestPoints(this.itemCount);
	}

	private void doNearestPoints(int size) {
		try {
			CLContext context = this.clInstance.getContext();
			CLQueue cmdQ = this.clInstance.getQueue();
			CLKernel kernel = this.clInstance.getKernel(PREFIX, KERNEL_DIST);
			if (kernel == null)
				kernel = this.clInstance.loadKernel(KERNEL_PATH, KERNEL_DIST,
						PREFIX);

			// copy buffer to device
			this.pointBuffer.write(cmdQ, 0, bufferCount,
					FloatBuffer.wrap(this.buffer), true, new CLEvent[0]);

			int globalSize = bufferCount;

			kernel.setArg(0, this.resultBuffer);
			kernel.setArg(1, this.pointBuffer);
			kernel.setArg(2, size);
			kernel.setArg(3, this.compareBuffer);
			kernel.setArg(4, COMPARE_ITEMS);
			kernel.setArg(5, DIM);

			kernel.enqueueNDRange(cmdQ, new int[] { globalSize },
					new CLEvent[0]);
			cmdQ.finish();

			FloatBuffer res = ByteBuffer
					.allocateDirect(bufferCount * SIZEOF_CL_FLOAT)
					.order(context.getByteOrder()).asFloatBuffer();

			resultBuffer.read(cmdQ, 0, bufferCount, res, true, new CLEvent[0]);

			cmdQ.finish();
			res.rewind();

			IPoint centroid;
			for (int i = 0; i < size; i++) {
				centroid = new Point(DIM);
				for (int d = 0; d < DIM; d++) {
					centroid.set(d, res.get());
				}
				itemBuffer[i].setCentroid(centroid);
			}
		} catch (CLException err) {
			Logger.logError(CLAZZ, "OpenCL error:\n" + err.getMessage() + "():"
					+ err.getCode());
			err.printStackTrace();
		} catch (Exception err) {
			Logger.logError(CLAZZ, "Error:\n" + err.getMessage() + "()");
			err.printStackTrace();
		}

		this.itemCount = 0;
		this.bufferCount = 0;
	}

}