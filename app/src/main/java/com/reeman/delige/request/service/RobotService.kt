package com.reeman.delige.request.service

import com.reeman.delige.repository.entities.DeliveryRecord
import com.reeman.delige.request.model.ApkInfo
import com.reeman.delige.request.model.ChargeRecord
import com.reeman.delige.request.model.FaultRecord
import com.reeman.delige.request.model.LoginResponse
import com.reeman.delige.request.model.MapVO
import com.reeman.delige.request.model.PathPointModel
import com.reeman.delige.request.model.Point
import com.reeman.delige.request.model.Response
import com.reeman.delige.request.model.StateRecord
import io.reactivex.rxjava3.core.Observable
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url


interface RobotService {
    @GET
    suspend fun getApkInfo(@Url url: String,@Query("api_token") apiToken:String):retrofit2.Response<ApkInfo?>

    @GET
    @Streaming
    suspend fun downloadApplication(@Url url: String): retrofit2.Response<ResponseBody>
    @GET
    fun getMapList(@Url url: String): Observable<List<MapVO>>

    @GET
    fun fetchPointsAsync(@Url url: String): Observable<Map<String, List<String>>>

    @GET
    fun fetchPoints(@Url url: String): Call<Map<String, List<Point>>>

    @GET
    fun fetchPathPoints(@Url url: String): Call<PathPointModel>

    @GET
    fun fetchRoutes(@Url url: String): Call<Map<String, List<List<Double>>>>


    @GET
    fun fetchMusic(@Url url: String): Observable<List<String>>

    @Streaming
    @POST
    fun download(@Url url: String, @Body map: Map<String, String>): Observable<ResponseBody>

    /**
     * 上报充电结果
     *
     * @param url
     * @param record
     * @return
     */
    @POST
    fun reportChargeResult(
        @Url url: String,
        @Body record: ChargeRecord
    ): Observable<Map<String, Any>>

    /**
     * 登录获取token
     *
     * @param url
     * @param loginModel
     * @return
     */
    @POST
    fun login(
        @Url url: String,
        @Body loginModel: Map<String, String>
    ): Observable<LoginResponse>

    /**
     * 同步登录获取token
     *
     * @param url
     * @param loginModel
     * @return
     */
    @POST
    fun loginSync(
        @Url url: String,
        @Body loginModel: Map<String, String>
    ): Call<LoginResponse>

    /**
     * 上报状态
     *
     * @param url
     * @param record
     * @return
     */
    @POST
    fun heartbeat(@Url url: String, @Body record: StateRecord): Call<Map<String, Any>>

    /**
     * 上报任务执行结果
     *
     * @param url
     * @param record
     * @return
     */
    @POST
    fun reportTaskResult(@Url url: String, @Body record: DeliveryRecord): Observable<Response>

    @POST
    fun reportTaskListResult(
        @Url url: String,
        @Body record: List<DeliveryRecord>
    ): Call<Map<String, Any>>

    /**
     * 上报硬件异常
     *
     * @param url
     * @param record
     * @return
     */
    @POST
    fun reportHardwareError(@Url url: String, @Body record: FaultRecord): Observable<Response>

    @POST
    fun savePath(
        @Url url: String,
        @Body list: Map<String, List<List<Float>>>
    ): Observable<Map<String, Any>>

    @POST
    fun savePathSync(
        @Url url: String,
        @Body list: Map<String, List<List<Double>>>
    ): Call<Map<String, Any>>

    @POST
    fun notify(
        @Url url: String,
        @Body body: Map<String, String>
    ): Observable<Map<String, Any>>
}
