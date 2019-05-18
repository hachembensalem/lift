
#include <bits/stdc++.h>

using namespace std;

    
namespace lift {; 
#ifndef TRAPZ_H
#define TRAPZ_H
; 
float trapz(float x1, float x2, float y1, float y2){
    { return (x2-x1)*(y2+y1)/2.0f; }; 
}

#endif
 ; 
#ifndef ADD_H
#define ADD_H
; 
float add(float l, float r){
    { return (l + r); }; 
}

#endif
 ; 
void trapz(float * v_initial_param_405_197, float * v_initial_param_406_198, float * & v_user_func_409_201, int v_N_0){
    // Allocate memory for output pointers
    float * v_user_func_433_200 = reinterpret_cast<float *>(malloc(((-1 + v_N_0) * sizeof(float))));
    v_user_func_409_201 = reinterpret_cast<float *>(malloc((1 * sizeof(float)))); 
    // For each element processed sequentially
    for (int v_i_196 = 0;(v_i_196 <= (-2 + v_N_0)); (++v_i_196)){
        v_user_func_433_200[v_i_196] = trapz(v_initial_param_405_197[v_i_196], v_initial_param_405_197[(1 + v_i_196)], v_initial_param_406_198[v_i_196], v_initial_param_406_198[(1 + v_i_196)]); 
    }
    // For each element reduced sequentially
    v_user_func_409_201[0] = 0.0f; 
    for (int v_i_195 = 0;(v_i_195 <= (-2 + v_N_0)); (++v_i_195)){
        v_user_func_409_201[0] = add(v_user_func_409_201[0], v_user_func_433_200[v_i_195]); 
    }
}
}; 