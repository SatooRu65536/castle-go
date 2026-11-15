package dev.satooru.castles.go.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/** POI アプリとして動作する Android Auto のエントリ． */
class CastleCarAppService : CarAppService() {
    // 公開前に，許可するホストの署名を絞ること（現在は開発・DHU での確認用）．
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = CastleCarSession()

}
