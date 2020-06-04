/**
 * Configuration classes needed to setup a static Bearer token authentication mechanism.
 * Such components can be configured following the example below:
 * <br><br>
 * <pre><code>
 * {@literal @}Configuration
 * {@literal @}EnableWebSecurity
 * public class SecurityConfig extends WebSecurityConfigurerAdapter {
 *
 *     {@literal @}Autowired
 *     private StaticBearerTokenAuthenticationProvider staticBearerTokenAuthenticationProvider;
 *
 *     {@literal @}Bean
 *     public StaticBearerTokenAuthenticationProvider staticBearerTokenAuthenticationProvider({@literal @}Value("${api.access.token}") String accessToken) {
 *         return new StaticBearerTokenAuthenticationProvider(accessToken, Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")));
 *     }
 *
 *     {@literal @}Override
 *     protected void configure(HttpSecurity http) throws Exception {
 *         http
 *             .csrf().disable()
 *             .exceptionHandling().authenticationEntryPoint(new UnauthorizedStatusAuthenticationEntryPoint()).and()
 *             .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
 *         http
 *             .authenticationProvider(staticBearerTokenAuthenticationProvider)
 *             .authorizeRequests()
 *             .antMatchers("/api/**").hasRole("USER")
 *             .anyRequest().fullyAuthenticated();
 *         http
 *             .apply(new BearerTokenAuthenticationFilterConfigurer());
 *     }
 * }
 * </code></pre>
 */
package net.optionfactory.spring.authentication.bearer.token;
