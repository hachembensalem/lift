
#include <bits/stdc++.h>

using namespace std;

    

#include <iostream>
#include <CL/cl.hpp>
#include <fstream>

std::string readFile(const char *filename){

  std::ifstream in(filename, std::ios::in);

  if (in.fail())
  {
  std::cerr << "Error reading file " << filename << std::endl;
  exit(1); }

  std::string contents;
  in.seekg(0, std::ios::end);
  contents.resize(in.tellg());
  in.seekg(0, std::ios::beg);
  in.read(&contents[0], contents.size());
  in.close();
  return contents;
  }

    

int platformId = 0;
int deviceId = 0;

 std::vector<cl::Platform> allPlatforms;
 cl::Platform platform;
 std::vector<cl::Device> allDevices;
 cl::Device device;
 cl::Context context;
 cl::CommandQueue lift_queue;

      ; 
std::string kernel_string_605188;
cl::Program::Sources kernel_source_605188;
cl::Program kernel_program_605188;
cl::Kernel kernel_605188;
; 
; 
; 
; 
; 
; 
; 
; 
; 
; 
; 
; 
void lift_init(){
    
	cl::Platform::get(&allPlatforms);
 if (allPlatforms.size() == 0) {
 std::cout << " No platforms found. Check OpenCL installation!" << std::endl;
 exit(1);
 }

 platform = allPlatforms[platformId];

 platform.getDevices(CL_DEVICE_TYPE_ALL, &allDevices);
 if (allDevices.size() == 0) {
 std::cerr << " No devices found. Check OpenCL installation!" << std::endl;
 exit(1);
 }

 device = allDevices[deviceId];

 std::cerr << "Using platform: " << platform.getInfo<CL_PLATFORM_NAME>() << std::endl;
 std::cerr << "Using device: " << device.getInfo<CL_DEVICE_NAME>() << std::endl;

 // create context
 cl::Context tmp_context({ device });
 context = std::move(tmp_context);

 // create queue
 cl::CommandQueue tmp_queue(context, device, CL_QUEUE_PROFILING_ENABLE);
 lift_queue = std::move(tmp_queue);
      ; 
    kernel_string_605188 = readFile("kernel_605188.cl"); 
    kernel_source_605188 = cl::Program::Sources(1, {kernel_string_605188.c_str(), kernel_string_605188.length()}); 
    kernel_program_605188 = cl::Program(context, kernel_source_605188); 
    if ((kernel_program_605188.build({ device }) != CL_SUCCESS)){
        std::cerr<<"kernel build error"<<std::endl; exit(1);; 
    }
    kernel_605188 = cl::Kernel(kernel_program_605188, "KERNEL"); 
}

double cpu_time_in_ms( std::chrono::milliseconds start, std::chrono::milliseconds finish ){
 return (finish - start).count();
}

double gpu_time_in_ms( cl::Event event ){

  cl_ulong start;
  cl_ulong end;
  cl_int err;

  event.wait();

  err = clGetEventProfilingInfo(event(), CL_PROFILING_COMMAND_START,
                                sizeof(start), &start, NULL);
  assert(err == CL_SUCCESS);

  err = clGetEventProfilingInfo(event(), CL_PROFILING_COMMAND_END,
                                sizeof(end), &end, NULL);
  assert(err == CL_SUCCESS);

  return ((double)(end - start)) * 1.0e-6;
}

double diff_percent(double lhs, double rhs) {
  if(std::min(lhs,rhs)==0)
    return -1;
  else
    return std::abs(lhs-rhs)/std::min(lhs,rhs);
}

          ; 
void print_clock(){
    std::cerr<<"func_name, cpu_time_ms, gpu_time_ms, diff_percentage"<<std::endl; 
}
void post_execute(){
    print_clock(); 
}


void execute(float * v_initial_param_605179_204756, float * v_initial_param_605180_204757, float * & v_user_func_605189_204761, int v_inputChannels_204154, int v_kernelChannels_204156, int v_kernelWidthHeight_204155, int v_nInputs_204158, int v_inputWidthHeight_204157){
    // Allocate memory for output pointers
    cl::Buffer v_user_func_605185_204758(context, CL_MEM_READ_WRITE, ((v_inputChannels_204154 * v_kernelChannels_204156 * (int)pow((float)v_kernelWidthHeight_204155, 2)) * sizeof(float)));
    cl::Buffer v_user_func_605187_204759(context, CL_MEM_READ_WRITE, ((v_inputChannels_204154 * v_nInputs_204158 * (int)pow((float)v_inputWidthHeight_204157, 2)) * sizeof(float)));
    cl::Buffer v_user_func_605188_204760(context, CL_MEM_READ_WRITE, (((v_inputChannels_204154 * v_kernelChannels_204156 * v_nInputs_204158 * (int)pow((float)((v_inputWidthHeight_204157 + v_kernelStride_204160 + (-1 * v_kernelWidthHeight_204155)) / v_tileStride_204159), 2) * (int)pow((float)v_kernelWidthHeight_204155, 2) * (int)pow((float)(v_tileStride_204159 / v_kernelStride_204160), 2))/(v_seqOpsPerThread_204161)) * sizeof(float)));
    v_user_func_605189_204761 = reinterpret_cast<float *>(malloc((((v_inputChannels_204154 * v_kernelChannels_204156 * v_nInputs_204158 * (int)pow((float)((v_inputWidthHeight_204157 + v_kernelStride_204160 + (-1 * v_kernelWidthHeight_204155)) / v_tileStride_204159), 2) * (int)pow((float)v_kernelWidthHeight_204155, 2) * (int)pow((float)(v_tileStride_204159 / v_kernelStride_204160), 2))/(v_seqOpsPerThread_204161)) * sizeof(float)))); 
    ; 
    lift_queue.enqueueWriteBuffer(v_user_func_605185_204758, CL_TRUE, 0, ((v_inputChannels_204154 * v_kernelChannels_204156 * (int)pow((float)v_kernelWidthHeight_204155, 2)) * sizeof(float)), v_initial_param_605179_204756, NULL, NULL); 
    ; 
    ; 
    ; 
    lift_queue.enqueueWriteBuffer(v_user_func_605187_204759, CL_TRUE, 0, ((v_inputChannels_204154 * v_nInputs_204158 * (int)pow((float)v_inputWidthHeight_204157, 2)) * sizeof(float)), v_initial_param_605180_204757, NULL, NULL); 
    ; 
    ; 
    kernel_605188.setArg(0, v_user_func_605185_204758); 
    kernel_605188.setArg(1, v_user_func_605187_204759); 
    kernel_605188.setArg(2, v_user_func_605188_204760); 
    kernel_605188.setArg(3, v_inputChannels_204154); 
    kernel_605188.setArg(4, v_kernelChannels_204156); 
    kernel_605188.setArg(5, v_kernelWidthHeight_204155); 
    kernel_605188.setArg(6, v_nInputs_204158); 
    kernel_605188.setArg(7, v_inputWidthHeight_204157); 
    
    lift_queue.enqueueNDRangeKernel(kernel_605188, cl::NullRange, cl::NDRange(1,1,1), cl::NDRange(1,1,1), NULL, NULL); 
    ; 
    
    ; 
    lift_queue.enqueueReadBuffer(v_user_func_605188_204760, CL_TRUE, 0, (((v_inputChannels_204154 * v_kernelChannels_204156 * v_nInputs_204158 * (int)pow((float)((v_inputWidthHeight_204157 + v_kernelStride_204160 + (-1 * v_kernelWidthHeight_204155)) / v_tileStride_204159), 2) * (int)pow((float)v_kernelWidthHeight_204155, 2) * (int)pow((float)(v_tileStride_204159 / v_kernelStride_204160), 2))/(v_seqOpsPerThread_204161)) * sizeof(float)), v_user_func_605189_204761, NULL, NULL); 
    ; 
    ; 
    post_execute(); 
}